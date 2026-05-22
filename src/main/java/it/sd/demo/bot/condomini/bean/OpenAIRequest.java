package it.sd.demo.bot.condomini.bean;

import java.util.List;

public class OpenAIRequest {

    private String model;

    private List<OpenAIRequestMessage> messages;

    private double temperature;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<OpenAIRequestMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<OpenAIRequestMessage> messages) {
        this.messages = messages;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
}