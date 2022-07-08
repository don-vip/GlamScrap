package com.github.donvip.archscrap.wikidata;

import static java.util.stream.Collectors.joining;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.common.iteration.Iterations;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;

public final class WikidataUtils {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final Map<String, Author> AUTHORS = new HashMap<>();

    private static final Map<String, String> OCCUPATION_QIDS = Map.of("photographe", "Q33231", "photographes", "Q33231",
            "studio photo.", "Q672070", "studio photographique", "Q672070", "agence photographique", "Q860572");

    private static final Map<String, List<String>> NOM_PRENOM_QIDS = new HashMap<>();

    private static final Pattern PATTERN_NOM_PRENOM_OCCUPATION = Pattern
            .compile("([çéèü\\p{Alpha}]+), +([çéèü\\p{Alpha}\\.]+) +\\((.*)\\)");
    private static final Pattern PATTERN_NOM_OCCUPATION = Pattern
            .compile("([çéèü\\p{Alpha}]+(?: +[çéèü\\p{Alpha}]+)*) +\\((.*)\\)");

    private static final SPARQLRepository sparqlRepository = new SPARQLRepository("https://query.wikidata.org/sparql");

    private WikidataUtils() {
        // Hide public constructor
    }

    public static Author retrieveAuthorInfo(String author, Map<String, Author> predefinedAuthors) {
        return AUTHORS.computeIfAbsent(author, k -> {
            final Author a = initAuthor(author, predefinedAuthors);
            final String occupationQid = OCCUPATION_QIDS.get(a.getOccupation());
            if (occupationQid != null) {
                try (RepositoryConnection sparqlConnection = sparqlRepository.getConnection()) {
                    String query = switch (occupationQid) {
                    case "Q33231": // photographe
                        yield getHumanQuery(sparqlConnection, a, occupationQid);
                    case "Q672070", "Q860572": // studio/agence photographique
                        yield getInstitutionQuery(a, occupationQid);
                    default:
                        throw new IllegalArgumentException("Unexpected value: " + occupationQid);
                    };
                    if (query != null) {
                        List<BindingSet> results = Iterations.asList(sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate());
                        if (results.size() != 1) {
                            LOGGER.info("Not exactly 1 author when looking for {} in Wikidata: {}", author, results);
                        } else {
                            BindingSet result = results.get(0);
                            Binding item = result.getBinding("item");
                            Binding label = result.getBinding("label");
                            Binding birth = result.getBinding("birth");
                            Binding death = result.getBinding("death");
                            Binding creator = result.getBinding("creator");
                            Binding institution = result.getBinding("institution");
                            a.setQid(item.getValue().stringValue());
                            if (birth != null) {
                                a.setBirthYear(ZonedDateTime.parse(birth.getValue().stringValue()).getYear());
                            }
                            if (death != null) {
                                a.setDeathYear(ZonedDateTime.parse(death.getValue().stringValue()).getYear());
                                boolean pd = a.isPublicDomain();
                                LOGGER.info("{} ({}) in public domain: {} (died in {})", item, label, pd, a.getDeathYear());
                                if (creator != null) {
                                    a.setCommonsCreator(creator.getValue().stringValue());
                                } else if (institution != null) {
                                    a.setCommonsInstitution(institution.getValue().stringValue());
                                } else if (pd) {
                                    LOGGER.error("No Commons Creator nor Institution for {} ({})", item, label);
                                }
                            } else {
                                LOGGER.info("No date of death for {} ({})", item, label);
                            }
                        }
                    }
                }
            }
            return a;
        });
    }

    private static Author initAuthor(String author, Map<String, Author> predefinedAuthors) {
        if (predefinedAuthors.containsKey(author)) {
            return predefinedAuthors.get(author);
        } else {
            Matcher m = PATTERN_NOM_PRENOM_OCCUPATION.matcher(author);
            if (m.matches()) {
                return new Author(m.group(1), m.group(2).endsWith(".") ? null : m.group(2).replace("Edouard", "Édouard"), m.group(3));
            } else {
                m = PATTERN_NOM_OCCUPATION.matcher(author);
                if (m.matches()) {
                    return new Author(m.group(1), null, m.group(2));
                } else {
                    LOGGER.info("Unsupported author: {}", author);
                    return new Author("", "", "");
                }
            }
        }
    }

    private static String getHumanQuery(RepositoryConnection sparqlConnection, Author a, String occupationQid) {
        List<String> nomsQid = findInWikidata(sparqlConnection, List.of("Q101352"), a.getNom());
        if (nomsQid.isEmpty()) {
            LOGGER.error("Unsupported name: {}", a.getNom());
            return null;
        }
        List<String> prenomsQid = a.getPrenom() != null
                ? findInWikidata(sparqlConnection, List.of("Q202444", "Q12308941", "Q11879590", "Q3409032"), a.getPrenom())
                : Collections.emptyList();
        StringBuilder sb = new StringBuilder("""
                SELECT DISTINCT ?item ?label ?birth ?death ?creator
                WHERE
                {
                """);
        if (!prenomsQid.isEmpty()) {
            sb.append("  VALUES ?prenoms { $prenoms }\n"
                    .replace("$prenoms", prenomsQid.stream().map(q -> "wd:" + q).collect(joining(" "))));
        }
        sb.append("""
                    ?item wdt:P31 wd:Q5;
                          wdt:P106 wd:$occupation;
                  """.replace("$occupation", occupationQid));

        if (!prenomsQid.isEmpty()) {
            sb.append(
                   "      wdt:P735 ?prenoms;\n");
        }
        return sb.append("""
                        wdt:P734 wd:$nom;
                        rdfs:label ?label.
                  OPTIONAL { ?item wdt:P569 ?birth }.
                  OPTIONAL { ?item wdt:P570 ?death }.
                  OPTIONAL { ?item wdt:P1472 ?creator }.
                  FILTER(LANG(?label) = "fr").
                }
                """.replace("$nom", nomsQid.get(0))).toString();
    }

    private static String getInstitutionQuery(Author a, String occupationQid) {
        return """
                SELECT DISTINCT ?item ?label ?birth ?death ?creator ?institution
                WHERE
                {
                  VALUES ?natures { wd:Q43229 wd:Q178706 wd:Q3152824 wd:Q2085381 wd:Q4830453 wd:$occupation }
                  VALUES ?noms { "$nom"@mul "$nom"@fr "$nom"@en "$nom"@es "$nom"@de "$nom"@it }
                  ?item wdt:P31 ?natures;
                        wdt:P1705 ?noms;
                        rdfs:label ?label.
                  OPTIONAL { ?item wdt:P571 ?birth }.
                  OPTIONAL { ?item wdt:P576 ?death }.
                  OPTIONAL { ?item wdt:P1472 ?creator }.
                  OPTIONAL { ?item wdt:P1612 ?institution }.
                  FILTER(LANG(?label) = "fr").
                }
                """.replace("$nom", a.getNom()).replace("$occupation", occupationQid);
    }

    protected static List<String> findInWikidata(RepositoryConnection sparqlConnection, List<String> naturesList, String textualValue) {
        String natures = naturesList.stream().map(q -> "wd:" + q).collect(joining(" "));
        return NOM_PRENOM_QIDS.computeIfAbsent(natures + "=" + textualValue, k -> {
            TupleQuery tupleQuery = sparqlConnection.prepareTupleQuery(QueryLanguage.SPARQL, """
                    SELECT DISTINCT ?item
                    WHERE
                    {
                       VALUES ?natures { $natures }
                       VALUES ?noms { "$textualValue"@mul "$textualValue"@fr "$textualValue"@en "$textualValue"@es "$textualValue"@de "$textualValue"@it "$textualValue"@co }
                       ?item wdt:P31 ?natures;
                       wdt:P1705 ?noms;
                    }
                """.replace("$natures", natures).replace("$textualValue", textualValue));
            List<BindingSet> results = Iterations.asList(tupleQuery.evaluate());
            if (results.isEmpty()) {
                LOGGER.error("No name/surname found when looking for {} {} in Wikidata: {}", natures, textualValue, results);
                return Collections.emptyList();
            } else {
                return results.stream().map(x -> x.getBinding("item").getValue().stringValue().substring(31)).toList();
            }
        });
    }
}
