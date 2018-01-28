/**
 * This file is part of ArchScrap.
 *
 *  ArchScrap is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ArchScrap is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with ArchScrap. If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.donvip.archscrap.domain;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
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
    @Column(length = 288)
    private String accessConditions;
    @Lob
    private String reuseConditions;
    @OrderBy("id")
    @OneToMany(mappedBy = "fonds")
    private List<Notice> notices = new ArrayList<>();

    public Fonds() {
        // Default constructor
    }

    public Fonds(String cote) {
        setCote(cote);
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
