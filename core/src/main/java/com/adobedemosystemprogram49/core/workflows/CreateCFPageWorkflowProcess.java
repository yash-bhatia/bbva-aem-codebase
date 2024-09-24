package com.adobedemosystemprogram49.core.workflows;

import java.util.Iterator;
import java.util.Random;

import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.granite.taskmanagement.Task;
import com.adobe.granite.taskmanagement.TaskManager;
import com.adobe.granite.taskmanagement.TaskManagerException;
import com.adobe.granite.taskmanagement.TaskManagerFactory;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.InboxItem;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.Replicator;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;

@Component(
        service = WorkflowProcess.class,
        property = {
            "process.label=Content Fragment Page Process",
            Constants.SERVICE_DESCRIPTION + "=Creates a page based on the content fragment",
            Constants.SERVICE_VENDOR + "=Adobe"
        }
)

public class CreateCFPageWorkflowProcess implements WorkflowProcess {

    private Replicator replicator;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap processArguments) throws WorkflowException {
        final WorkflowData workflowData = workItem.getWorkflowData();
        final String payloadPath = workflowData.getPayload().toString();
        final ResourceResolver resourceResolver = workflowSession.adaptTo(ResourceResolver.class);
        PageManager pageManager = resourceResolver.adaptTo(PageManager.class);
        javax.jcr.Session session = resourceResolver.adaptTo(javax.jcr.Session.class);

        try { // replicating the content fragment
            replicator.replicate(session, ReplicationActionType.ACTIVATE, payloadPath);
        } catch (Exception e) {
            logger.error("an error occured while replicating the pages");
        }

        logger.debug("Content Fragment Path :: {}", payloadPath);
        String[] params = processArguments.get("PROCESS_ARGS", "string").split(",");
        String referencePagePath = params[0];
        String parentPagePath = params[1];
        logger.debug("Reference Page : {}, Parent Path : {}", referencePagePath, parentPagePath);
        Resource fragmentResource = resourceResolver.getResource(payloadPath);
        String title = "";
        String description = "";
        String link = "";
        String image = "";
        if (fragmentResource != null) {
            ContentFragment fragment = fragmentResource.adaptTo(ContentFragment.class);
            Iterator<ContentElement> elements = fragment.getElements();
            while (elements.hasNext()) {
                ContentElement element = elements.next();
                logger.debug("CF Element {} :: {}", element.getName(), element.getContent());
                if (element.getName().equals("title") && !element.getContent().isEmpty()) {
                    title = element.getContent();
                }
                if (element.getName().equals("subtitle") && !element.getContent().isEmpty()) {
                    description = element.getContent();
                }
                if (element.getName().equals("callToAction") && !element.getContent().isEmpty()) {
                    link = element.getContent();
                }
                if (element.getName().equals("bakgroundImage") && !element.getContent().isEmpty()) {
                    image = element.getContent();
                }
            }
        }
        try {
            Page page = pageManager.getContainingPage(referencePagePath);
            Random random = new Random();
            //creating random integer
            int lowerBound = 1;
            int upperBound = 1000;
            int randomInt = random.nextInt(upperBound - lowerBound) + lowerBound;
            
            Page newPage = pageManager.copy(page, parentPagePath + "/test-page" + Integer.toString(randomInt), null, true, true);

            ModifiableValueMap contentValues = newPage.getContentResource().adaptTo(ModifiableValueMap.class);
            contentValues.put(JcrConstants.JCR_TITLE, title);
            Iterable<Resource> resources = newPage.getContentResource().getChildren();
            for (Resource resource : resources) {
                if (resource != null) {
                    logger.debug("Resource component : {}", resource.getName());
                    updateFragmentContent(resource, title, description, link, image);

                    try { //replicating the pages
                        replicator.replicate(session, ReplicationActionType.ACTIVATE, newPage.getPath());
                    } catch (Exception e) {
                        logger.error("an error occured while replicating the pages");
                    }

                }
            }
            resourceResolver.commit();

            TaskManager taskManager = resourceResolver.adaptTo(TaskManager.class);
            TaskManagerFactory taskManagerFactory = taskManager.getTaskManagerFactory();
            Task task = taskManagerFactory.newTask(Task.DEFAULT_TASK_TYPE);
            task.setName("New page based on CF created");
            task.setDescription("New page based on CF created");
            task.setInstructions("New page based on CF created");
            task.setPriority(InboxItem.Priority.HIGH);
            task.setContentPath(newPage.getPath());
            taskManager.createTask(task);
        } catch (WCMException e) {
            logger.error("WCMException in Page Creation", e);
        } catch (PersistenceException e) {
            logger.error("PersistenceException in Page Creation", e);
        } catch (TaskManagerException e) {
            logger.error("TaskManagerException in Task Creation", e);
        }
    }

    private void updateFragmentContent(Resource resource, String title, String description, String link, String image) throws PersistenceException {
        if (resource.hasChildren()) {
            for (Resource childResource : resource.getChildren()) {
                updateFragmentContent(childResource, title, description, link, image);
            }
        }
        ModifiableValueMap valueMap = resource.adaptTo(ModifiableValueMap.class);
        if (valueMap.containsKey("name") && valueMap.get("name").equals("Banner")) {
            valueMap.put("title", "<h1>" + title + "</h1>");
            valueMap.put("description", description);
            valueMap.put("link", link);
        }
        if (valueMap.containsKey("name") && valueMap.get("name").equals("Bannerimage")) {
            String[] imageArray = new String[1];
            imageArray[0] = image;
            valueMap.put("image", imageArray);
        }
        resource.getResourceResolver().commit();

    }
}
