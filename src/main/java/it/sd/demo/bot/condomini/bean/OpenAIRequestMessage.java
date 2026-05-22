package it.sd.demo.bot.condomini.bean;

public class OpenAIRequestMessage {

    private String role;
    private String content;

    public OpenAIRequestMessage() {
    }

    public OpenAIRequestMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}