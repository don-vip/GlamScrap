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

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "Notices")
public class Notice {

    @Id
    private String cote;
    private int id;
    private String title;
    @Column(length = 3328)
    private String description;
    private LocalDate date;
    private YearMonth yearMonth;
    private Year year;
    private String type;
    private String technique;
    @ElementCollection
    private List<String> authors = new ArrayList<>();
    private String format;
    private String support;
    private String materialCondition;
    @ManyToOne
    private Fonds fonds;
    private String producer;
    private String classification;
    private String origin;
    private String entryMode;
    private Year entryYear;
    @Column(length = 896)
    private String rights;
    private boolean originalConsultable;
    @Column(length = 2560)
    private String observation;
    @ElementCollection
    private List<String> indexation = new ArrayList<>();
    private String historicalPeriod;

    public Notice() {
        // Default constructor
    }

    public Notice(String cote) {
        setCote(cote);
        Matcher m = Pattern.compile("(\\d+[A-Z][a-z]+)(\\d+)").matcher(cote);
        if (m.matches()) {
            setId(Integer.valueOf(m.group(2)));
        }
    }

    public String getCote() {
        return cote;
    }

    public void setCote(String cote) {
        this.cote = cote;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public YearMonth getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(YearMonth yearMonth) {
        this.yearMonth = yearMonth;
    }

    public Year getYear() {
        return year;
    }

    public void setYear(Year year) {
        this.year = year;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTechnique() {
        return technique;
    }

    public void setTechnique(String technique) {
        this.technique = technique;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public void setAuthors(List<String> authors) {
        this.authors = authors;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getSupport() {
        return support;
    }

    public void setSupport(String support) {
        this.support = support;
    }

    public String getMaterialCondition() {
        return materialCondition;
    }

    public void setMaterialCondition(String materialCondition) {
        this.materialCondition = materialCondition;
    }

    public Fonds getFonds() {
        return fonds;
    }

    public void setFonds(Fonds fonds) {
        this.fonds = fonds;
    }

    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getEntryMode() {
        return entryMode;
    }

    public void setEntryMode(String entryMode) {
        this.entryMode = entryMode;
    }

    public Year getEntryYear() {
        return entryYear;
    }

    public void setEntryYear(Year entryYear) {
        this.entryYear = entryYear;
    }

    public String getRights() {
        return rights;
    }

    public void setRights(String rights) {
        this.rights = rights;
    }

    public boolean isOriginalConsultable() {
        return originalConsultable;
    }

    public void setOriginalConsultable(boolean originalConsultable) {
        this.originalConsultable = originalConsultable;
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    public List<String> getIndexation() {
        return indexation;
    }

    public void setIndexation(List<String> indexation) {
        this.indexation = indexation;
    }

    public String getHistoricalPeriod() {
        return historicalPeriod;
    }

    public void setHistoricalPeriod(String historicalPeriod) {
        this.historicalPeriod = historicalPeriod;
    }

    @Override
    public String toString() {
        return "Notice [cote=" + cote + ", title=" + title + ']';
    }
}
