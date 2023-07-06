package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class NewsData {

    private final String title;
    private final String link;
    private final String imageLink;
    private final String description;
    private final String date;

    public NewsData(String title, String link, String imageLink, String description, String date) {
        this.title = title;
        this.link = link;
        this.imageLink = imageLink;
        this.description = description;
        this.date = date;
    }

    @Override
    public String toString() {
        return "NewsData{" +
                "title='" + title + '\'' +
                ", link='" + link + '\'' +
                ", imageLink='" + imageLink + '\'' +
                ", description='" + description + '\'' +
                ", date='" + date + '\'' +
                '}';
    }
}