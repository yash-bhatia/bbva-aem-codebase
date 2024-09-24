package com.adobedemosystemprogram49.core.beans;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatGPTRequest {
	private String model;
	private List<Message> messages;

	public ChatGPTRequest(String text, String model, String role) {
		this.model = model;
		this.messages = new ArrayList<>();
		Message message = new Message();
		message.setRole(role);
		message.setContent(text);
		this.messages.add(message);
	}

}
