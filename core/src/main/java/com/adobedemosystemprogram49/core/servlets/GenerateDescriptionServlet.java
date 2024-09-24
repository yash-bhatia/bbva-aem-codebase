package com.adobedemosystemprogram49.core.servlets;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.granite.taskmanagement.Task;
import com.adobe.granite.taskmanagement.TaskManager;
import com.adobe.granite.taskmanagement.TaskManagerException;
import com.adobe.granite.taskmanagement.TaskManagerFactory;
import com.adobe.granite.workflow.exec.InboxItem;
import com.adobedemosystemprogram49.core.beans.ChatGPTRequest;
import com.adobedemosystemprogram49.core.beans.ChatGptResponse;
import com.adobedemosystemprogram49.core.services.ChatGPTService;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.Replicator;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;

import javax.jcr.*;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.version.VersionException;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component(immediate = true, service = Servlet.class, property = {
        Constants.SERVICE_DESCRIPTION + "=ChatGPT Integration", "sling.servlet.methods=" + HttpConstants.METHOD_GET,
        "sling.servlet.paths=" + "/bin/gen-ai/variations"})

public class GenerateDescriptionServlet extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(GenerateDescriptionServlet.class);
    private static final HttpClient client = HttpClients.createDefault();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Reference
    private ChatGPTService chatGPTService;

    @Reference
    private Replicator replicator;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        ResourceResolver resourceResolver = request.getResourceResolver();
        String number_variations = request.getParameter("number_variations") != null ? request.getParameter("number_variations") : "3";
        String tone_of_voice = request.getParameter("tone_of_voice") != null ? request.getParameter("tone_of_voice") : "optimistic, smart, engaging, human, and creative";
        String master_title = request.getParameter("title") != null ? request.getParameter("title") : "You are one step away from choosing the number of months you will take to pay this bill";
        String master_subtitle = request.getParameter("subtitle") != null ? request.getParameter("subtitle") : "Defer your bill for this month over as many months as you choose, from 3 to 36 months. You can keep using your card as usual";
        String master_callToAction = request.getParameter("call_to_action") != null ? request.getParameter("call_to_action") : "Calculate";
        String master_image = request.getParameter("image_path") != null ? request.getParameter("image_path") : "/content/dam/bbva/widen-the-talent-pool.png";
        String explain_intent = request.getParameter("explain_intent") != null ? request.getParameter("explain_intent") : "engage users, encouraging them to click and interact more with our website content so they can make an informed purchase decision";
        String target_audience = request.getParameter("target_audience") != null ? request.getParameter("target_audience") : "Travel and Hospitality: These individuals enjoy exploring new places, cultures, and cuisines. They value hassle-free booking experiences, quality accommodations, unique experiences, and excellent customer service";
        String explain_interaction = request.getParameter("explain_interaction") != null ? request.getParameter("explain_interaction") : "the user sees a product page displaying a financial offer that resonates with the user's desire of a credit card. Here, they can quickly choose to login to the private area and activate the offer";
        String text = String.format("On our webpage, users are presented with a Hero banner. A Hero banner is an interface element "
                        + "which creates an immediate impact with its visually striking image on the right and compelling text on the left, including a title, body, and call-to-action button. "
                        + "It effectively communicates the website's core message, capturing users' attention and guiding them towards the desired action. Upon interacting with the Hero banner, "
                        + "%s.\n\nOur intent is to engage users, encouraging them to click and %s "
                        + "decision.\n\nYour assigned target audience is %s.\n\nYour task is to compose %s distinct piece(s) of copy for a Hero banner, "
                        + "targeted to our target audience, that is concise, engaging and persuasive to the user by selecting any available content provided in double-brackets ([[]]) below. "
                        + "The text you compose will be used to test the hypotheses below in a live experiment. Keep in mind the specific traits of our target audience, considering that users"
                        + "will typically only read the title.\n\nTo accomplish your task, the text you compose must strictly comply with the following requirements listed in triple backticks "
                        + "(```) below and address the following hypotheses:\n- Users will be more likely to engage with the Hero banner if it is personalized to their interests.\n- Users will be "
                        + "more likely to engage with the Hero banner if the Call-to-Action is clear, concise and relevant to the page content.\n- Users will be more likely to engage with the Hero "
                        + "banner if the text is concise, persuasive and engaging.\n\nRequirements: ```\n- The text must consist of four parts: a Title, a Body, a Call-to-Action and an \"AI Rationale\".\n- "
                        + "The text must be brief, such that:\n* In 20 words (100 characters) or less, compose the \"AI Rationale\" text first and use it to explain your reasoning for composing the copy, "
                        + "before composing the other parts.\n* The Title text must not exceed 7 words or 35 characters, including spaces.\n* The Body text must not exceed 15 words or 75 characters, "
                        + "including spaces.\n* The Call-to-Action text must not exceed 4 words or 20 characters, including spaces.\n- The tone of the text needs to be: %s\n- The product name must appear once either in the Title text or Body text.\n- Never abbreviate or shorten the name of the product in the text.\n- Compose "
                        + "the text without using the same adjective more than once.\n- Do not use exclamation marks or points at the end of sentences in the text.\n- Do not use exclamation marks or "
                        + "points in the text.\n- Format the response as an array of valid, iterable RFC8259 compliant JSON. Always list the \"AI Rationale\" attribute last.\n```\n\nAdditional "
                        + "Context: [[\n  - %s\n " + "%s\n" + "%s\n",
                explain_interaction, explain_intent, target_audience, number_variations, tone_of_voice, master_title, master_subtitle, master_callToAction);
        String message = generateMessage(text);
        Gson gson = new Gson();
        JsonArray jsonArray = gson.fromJson(message, JsonArray.class);
        createContentFragment(master_title, master_subtitle, master_callToAction, master_image, 1);
        List<String> pagePaths = new ArrayList<>();
        for (int i = 0; i < jsonArray.size(); i++) {
            String title = jsonArray.get(i).getAsJsonObject().get("Title").getAsString();
            String subtitle = jsonArray.get(i).getAsJsonObject().get("Body").getAsString();
            String callToAction = jsonArray.get(i).getAsJsonObject().get("Call-to-Action").getAsString();
            String backgroundImage = "/content/dam/bbva/widen-the-talent-pool.png";
            String path = createContentFragment(title, subtitle, callToAction, backgroundImage, 2);
            pagePaths.add(createPageFromFragment(path, resourceResolver, title, subtitle, callToAction, backgroundImage));
        }
        response.getWriter().write(pagePaths.toString());
    }

    private String generateMessage(final String text) throws IOException {

        String requestBody = MAPPER.writeValueAsString(
                new ChatGPTRequest(text, chatGPTService.getChatGPTModel(), chatGPTService.getChatGPTRole()));
        HttpPost request = new HttpPost(chatGPTService.getChatGPTHostname() + chatGPTService.getChatGPTApiEndpoint());
        request.addHeader("Authorization", "Bearer " + chatGPTService.getChatGPTApiKey());
        request.addHeader("Content-Type", "application/json");
        request.setEntity(new StringEntity(requestBody));
        HttpResponse response = client.execute(request);
        ChatGptResponse chatGptResponse = MAPPER.readValue(EntityUtils.toString(response.getEntity()),
                ChatGptResponse.class);

        return chatGptResponse.getChoices().get(0).getMessage().getContent();
    }

    private String createContentFragment(String title, String subtitle, String callToAction, String backgroundImage, int check) {
        String cfOutputPath = "";
        try {
            LOGGER.debug("Creating content fragment");

            String credToEncode = "servlet-user:nq9s5RjZHS7Czm9";
            /*OkHttpClient client = new OkHttpClient().newBuilder().build();
            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(mediaType, "{\n  \"properties\": {\n    \"cq:model\": \"/conf/bbva/settings/dam/cfm/models/banners\",\n    \"title\": \"test\",\n    \"description\": \"test\",\n    \"elements\": {\n      \"bakgroundImage\": {\n        \"value\": \"/content/dam/bbva/Hero Banner Image.png\",\n        \":type\": \"String\"\n      },\n      \"callToAction\": {\n        \"value\": \"Book Now\",\n        \":type\": \"String\"\n      },\n      \"subtitle\": {\n        \"value\": \"Explore new cultures, cuisines, and more with hassle-free bookings and unique experiences.\",\n        \":type\": \"String\"\n      },\n      \"title\": {\n        \"value\": \"Discover Your Dream Destinations\",\n        \":type\": \"String\"\n      }\n    }\n  }\n}");
            Request request = new Request.Builder()
                    .url("https://author-p139727-e1420399.adobeaemcloud.com/api/assets/bbva/banner-variations/test-sample")
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Basic " + encrypted)
                    .build();
            Response response = client.newCall(request).execute();*/
            // Define the URL
            URL url = new URL("https://author-p139727-e1420399.adobeaemcloud.com/api/assets/bbva/banner-variations/master");
            int lowerBound = 1;
            int upperBound = 1000;
            Random random = new Random();
            int randomInt = random.nextInt(upperBound - lowerBound) + lowerBound;
            String tempTitle = "Banner Master" + randomInt;
            if (check == 2) {
                String randomIntString = Integer.toString(randomInt);
                cfOutputPath = "/content/dam/bbva/banner-variations/banner-variant-" + randomIntString;
                url = new URL("https://author-p139727-e1420399.adobeaemcloud.com/api/assets/bbva/banner-variations/banner-variant-" + randomIntString);
                tempTitle = "test" + randomIntString;
            }
            LOGGER.debug("CF Output Path :: {}", cfOutputPath);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set the request method to POST
            connection.setRequestMethod("POST");

            // Set headers
            connection.setRequestProperty("Content-Type", "application/json");
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(credToEncode.getBytes());
            connection.setRequestProperty("Authorization", basicAuth);

            // Enable input/output streams
            connection.setDoOutput(true);
            // Define the JSON payload
            String jsonInputString = "{\n"
                    + "  \"properties\": {\n"
                    + "    \"cq:model\": \"/conf/bbva/settings/dam/cfm/models/banners\",\n"
                    + "    \"title\": \"" + tempTitle + "\",\n"
                    + "    \"description\": \"" + tempTitle + "\",\n"
                    + "    \"elements\": {\n"
                    + "      \"bakgroundImage\": {\n"
                    + "        \"value\": \"" + backgroundImage + "\",\n"
                    + "        \":type\": \"String\"\n"
                    + "      },\n"
                    + "      \"callToAction\": {\n"
                    + "        \"value\": \"" + callToAction + "\",\n"
                    + "        \":type\": \"String\"\n"
                    + "      },\n"
                    + "      \"subtitle\": {\n"
                    + "        \"value\": \"" + subtitle + "\",\n"
                    + "        \":type\": \"String\"\n"
                    + "      },\n"
                    + "      \"title\": {\n"
                    + "        \"value\": \"" + title + "\",\n"
                    + "        \":type\": \"String\"\n"
                    + "      }\n"
                    + "    }\n"
                    + "  }\n"
                    + "}";

            // Write the JSON data to the request body
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Get the response code
            String responseCode = connection.getResponseMessage();
            LOGGER.debug("Response Code: {}", responseCode);

            // Optional: Read the response (if needed)
            /*
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println(response.toString());
            }
             */
            // Disconnect the connection
            connection.disconnect();

        } catch (Exception e) {
            LOGGER.error("Error while creating content fragment :: {}", e.getMessage());
        }
        return cfOutputPath;
    }

    private String createPageFromFragment(String payloadPath, ResourceResolver resourceResolver, String title, String subtitle, String callToAction, String backgroundImage) {
        String newPagePath = "";
        PageManager pageManager = resourceResolver.adaptTo(PageManager.class);
        javax.jcr.Session session = resourceResolver.adaptTo(javax.jcr.Session.class);

        try { // replicating the content fragment
            replicator.replicate(session, ReplicationActionType.ACTIVATE, payloadPath);
        } catch (Exception e) {
            LOGGER.error("an error occured while replicating the pages");
        }

        LOGGER.debug("Content Fragment Path :: {}", payloadPath);
        String referencePagePath = "/content/bbva/index";
        String parentPagePath = "/content/bbva/index";
        LOGGER.debug("Reference Page : {}, Parent Path : {}", referencePagePath, parentPagePath);
        Resource fragmentResource = resourceResolver.getResource(payloadPath);
        if (fragmentResource != null) {
            LOGGER.debug("Fragment Resource :: {}", fragmentResource.getPath());
            ContentFragment fragment = fragmentResource.adaptTo(ContentFragment.class);
            Iterator<ContentElement> elements = fragment.getElements();
            while (elements.hasNext()) {
                ContentElement element = elements.next();
                LOGGER.debug("CF Element {} :: {}", element.getName(), element.getContent());
                if (element.getName().equals("title") && !element.getContent().isEmpty()) {
                    title = element.getContent();
                }
                if (element.getName().equals("subtitle") && !element.getContent().isEmpty()) {
                    subtitle = element.getContent();
                }
                if (element.getName().equals("callToAction") && !element.getContent().isEmpty()) {
                    callToAction = element.getContent();
                }
                if (element.getName().equals("bakgroundImage") && !element.getContent().isEmpty()) {
                    backgroundImage = element.getContent();
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

            Page newPage = pageManager.copy(page, parentPagePath + "/test-page" + randomInt, null, true, true);

            ModifiableValueMap contentValues = newPage.getContentResource().adaptTo(ModifiableValueMap.class);
            contentValues.put(JcrConstants.JCR_TITLE, title);
            Iterable<Resource> resources = newPage.getContentResource().getChildren();
            for (Resource resource : resources) {
                if (resource != null) {
                    updateFragmentContent(resource, title, subtitle, callToAction, backgroundImage, resourceResolver);
                    resourceResolver.commit();
                    session.save();
                    try { //replicating the pages
                        replicator.replicate(session, ReplicationActionType.ACTIVATE, newPage.getPath());
                    } catch (Exception e) {
                        LOGGER.error("an error occured while replicating the pages");
                    }

                }
            }
            resourceResolver.commit();
            newPagePath = newPage.getPath();
            TaskManager taskManager = resourceResolver.adaptTo(TaskManager.class);
            TaskManagerFactory taskManagerFactory = taskManager.getTaskManagerFactory();
            Task task = taskManagerFactory.newTask(Task.DEFAULT_TASK_TYPE);
            task.setName("New page based on CF created");
            task.setDescription("New page based on CF created");
            task.setInstructions("New page based on CF created");
            task.setPriority(InboxItem.Priority.HIGH);
            task.setContentPath(newPagePath);
            taskManager.createTask(task);
        } catch (WCMException e) {
            LOGGER.error("WCMException in Page Creation", e);
        } catch (PersistenceException e) {
            LOGGER.error("PersistenceException in Page Creation", e);
        } catch (TaskManagerException e) {
            LOGGER.error("TaskManagerException in Task Creation", e);
        } catch (AccessDeniedException e) {
            throw new RuntimeException(e);
        } catch (LockException e) {
            throw new RuntimeException(e);
        } catch (ItemExistsException e) {
            throw new RuntimeException(e);
        } catch (ReferentialIntegrityException e) {
            throw new RuntimeException(e);
        } catch (InvalidItemStateException e) {
            throw new RuntimeException(e);
        } catch (ConstraintViolationException e) {
            throw new RuntimeException(e);
        } catch (VersionException e) {
            throw new RuntimeException(e);
        } catch (NoSuchNodeTypeException e) {
            throw new RuntimeException(e);
        } catch (RepositoryException e) {
            throw new RuntimeException(e);
        }
        return newPagePath;
    }

    private void updateFragmentContent(Resource resource, String title, String description, String link, String image, ResourceResolver resourceResolver) throws PersistenceException {
        try{
            if (resource.hasChildren()) {
                for (Resource childResource : resource.getChildren()) {
                    updateFragmentContent(childResource, title, description, link, image, resourceResolver);
                }
            }
            ModifiableValueMap valueMap = resource.adaptTo(ModifiableValueMap.class);
            if (valueMap.containsKey("name") && valueMap.get("name").equals("Banner")) {
                LOGGER.debug("Resource component : {}", resource.getPath());
                LOGGER.debug("Updating fragment content - title : {}, description : {}, link : {}, image : {}", title, description, link, image);
                valueMap.put("title", "<h1>" + title + "</h1>");
                valueMap.put("description", description);
                valueMap.put("link", link);
                LOGGER.debug("Resource Resolver :: {}", resourceResolver.hasChanges());
            }
            if (valueMap.containsKey("name") && valueMap.get("name").equals("Bannerimage")) {
                String[] imageArray = new String[1];
                imageArray[0] = image;
                valueMap.put("image", imageArray);
            }
            resourceResolver.commit();
        } catch (Exception e) {
            LOGGER.error("Error while updating fragment content :: {}", e.getMessage());
        }

    }
}
