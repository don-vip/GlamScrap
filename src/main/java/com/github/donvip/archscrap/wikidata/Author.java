package com.github.donvip.archscrap.wikidata;

import java.time.LocalDate;

public final class Author {
    private final String nom;
    private final String prenom;
    private final String occupation;
    private String qid;
    private int birthYear = Integer.MIN_VALUE;
    private int deathYear = Integer.MIN_VALUE;
    private String commonsCreator;
    private String commonsInstitution;

    public Author(String nom, String prenom, String occupation) {
        this.nom = nom;
        this.prenom = prenom;
        this.occupation = occupation;
    }

    public String getNom() {
        return nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public String getOccupation() {
        return occupation;
    }

    public String getQid() {
        return qid;
    }

    public void setQid(String qid) {
        this.qid = qid;
    }

    public int getBirthYear() {
        return birthYear;
    }

    public void setBirthYear(int birthYear) {
        this.birthYear = birthYear;
    }

    public int getDeathYear() {
        return deathYear;
    }

    public void setDeathYear(int deathYear) {
        this.deathYear = deathYear;
    }

    public Boolean isPublicDomain() {
        return deathYear > Integer.MIN_VALUE && LocalDate.now().getYear() - deathYear >= 71;
    }

    public String getCommonsCreator() {
        return commonsCreator;
    }

    public void setCommonsCreator(String commonsCreator) {
        this.commonsCreator = commonsCreator;
    }

    public String getCommonsInstitution() {
        return commonsInstitution;
    }

    public void setCommonsInstitution(String commonsInstitution) {
        this.commonsInstitution = commonsInstitution;
    }

    @Override
    public String toString() {
        return nom + (prenom != null ? ", " + prenom : "") + " (" + occupation + ')';
    }
}