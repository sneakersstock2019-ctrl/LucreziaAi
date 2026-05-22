package it.sd.demo.bot.condomini.bean;

public class AIResponse {

    private String reply;
    private boolean open_ticket;
    private String category;
    private String priority;

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public boolean isOpen_ticket() {
        return open_ticket;
    }

    public void setOpen_ticket(boolean open_ticket) {
        this.open_ticket = open_ticket;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }
}