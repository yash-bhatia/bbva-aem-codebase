package com.adobedemosystemprogram49.core.services.impl;

import com.adobedemosystemprogram49.core.services.ChatGPTService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@Component(
        service = ChatGPTService.class,
        immediate = true)
@Designate(ocd = ChatGPTServiceImpl.ChatGPTConfiguration.class)
public class ChatGPTServiceImpl implements ChatGPTService {

	private String chatGPTHostname;
	private String chatGPTApiEndpoint;
	private String chatGPTApiKey;
	private String chatGPTRole;
	private String chatGPTModel;

	@Activate
	@Modified
	protected void Activate(final ChatGPTConfiguration chatGPTConfiguration) {
		chatGPTHostname = chatGPTConfiguration.chatGPTApiHostname();
		chatGPTApiEndpoint = chatGPTConfiguration.chatGPTApiEndpoint();
		chatGPTApiKey = chatGPTConfiguration.chatGPTApiKey();
		chatGPTRole = chatGPTConfiguration.chatGPTRole();
		chatGPTModel = chatGPTConfiguration.chatGPTModel();
	}

	@ObjectClassDefinition(name = "Chat GPT Configuration")
	public @interface ChatGPTConfiguration {

		@AttributeDefinition(name = "Chat GPT API Hostname", description = "API Hostname for Chat GPT")
		String chatGPTApiHostname() default "https://api.openai.com";

		@AttributeDefinition(name = "Chat GPT API Endpoint", description = "API Endpoint for Chat GPT")
		String chatGPTApiEndpoint() default "/v1/chat/completions";

		@AttributeDefinition(name = "Chat GPT API Key", description = "API Key for Chat GPT")
		String chatGPTApiKey() default "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";

		@AttributeDefinition(name = "Chat GPT Role", description = "The ‘role’ can take one of three values: ‘system’, ‘user’ or the ‘assistant’")
		String chatGPTRole() default "user";

		@AttributeDefinition(name = "Chat GPT Model", description = "The AI Model to be used")
		String chatGPTModel() default "gpt-3.5-turbo";

	}

	@Override
	public String getChatGPTApiEndpoint() {
		return chatGPTApiEndpoint;
	}

	@Override
	public String getChatGPTHostname() {
		return chatGPTHostname;
	}

	@Override
	public String getChatGPTApiKey() {
		return chatGPTApiKey;
	}

	@Override
	public String getChatGPTRole() {
		return chatGPTRole;
	}

	@Override
	public String getChatGPTModel() {
		return chatGPTModel;
	}

}
