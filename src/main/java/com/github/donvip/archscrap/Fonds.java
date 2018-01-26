package com.github.donvip.archscrap;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;

import org.hibernate.Session;

@Entity
public class Fonds {
    @Id
    private String cote;
    private String title;
    private int expectedNotices;
    @Lob
    private String note;
    @Lob
    private String summary;
    @Lob
    private String accessConditions;
    @Lob
    private String reuseConditions;
    @OrderBy("id")
    @OneToMany(mappedBy = "fonds")
    private List<Notice> notices = new ArrayList<>();

    public Fonds() {
        // Default constructor
    }

    public Fonds(String cote, String title) {
        setCote(cote);
        setTitle(title);
    }

    public String getCote() {
        return cote;
    }

    public void setCote(String cote) {
        this.cote = cote;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Notice> getNotices() {
        return notices;
    }

    public void setNotices(List<Notice> notices) {
        this.notices = notices;
    }

    public int getExpectedNotices() {
        return expectedNotices;
    }

    public void setExpectedNotices(int expectedNotices) {
        this.expectedNotices = expectedNotices;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getAccessConditions() {
        return accessConditions;
    }

    public void setAccessConditions(String accessConditions) {
        this.accessConditions = accessConditions;
    }

    public String getReuseConditions() {
        return reuseConditions;
    }

    public void setReuseConditions(String reuseConditions) {
        this.reuseConditions = reuseConditions;
    }

    @SuppressWarnings("unchecked")
    public List<Integer> getMissingNotices(Session session) {
        // https://stackoverflow.com/a/48446303/2257172
        return session.createNativeQuery(String.format(
                "SELECT id FROM UNNEST (SEQUENCE_ARRAY((SELECT MIN(id) FROM Notices), (SELECT MAX(id) FROM Notices), 1)) SEQ(id)" + 
                "LEFT OUTER JOIN Notices ON Notices.id = SEQ.id WHERE Notices.id IS NULL AND Notices.fonds_cote = '%s'", cote)).list();
    }

    @Override
    public String toString() {
        return "Fonds [cote=" + cote + ", title=" + title + ']';
    }
}
