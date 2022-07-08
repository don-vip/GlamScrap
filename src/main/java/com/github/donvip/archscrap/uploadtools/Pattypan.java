package com.github.donvip.archscrap.uploadtools;

import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.github.donvip.archscrap.ArchScrap;
import com.github.donvip.archscrap.domain.Fonds;
import com.github.donvip.archscrap.domain.Notice;
import com.github.donvip.archscrap.wikidata.Author;
import com.github.donvip.archscrap.wikidata.WikidataUtils;

public class Pattypan extends UploadTool {

    private static final Map<String, List<String>> TEMPLATE_COLUMNS = Map.of("photograph",
            Arrays.asList("path", "name", "photographer", "title", "description", "depicted_people", "depicted_place",
                    "date", "medium", "dimensions", "institution", "department", "references", "object_history",
                    "exhibition_history", "credit_line", "inscriptions", "notes", "accession_number", "source",
                    "permission", "other_versions", "other_fields", "license", "partnership", "categories"));

    @Override
    protected String getFileExtension() {
        return "xls";
    }

    @Override
    protected void writeContents(Fonds f, ArchScrap cityScrap, OutputStream out) throws IOException {
        try (Workbook wb = new HSSFWorkbook()) {
            Path downloadDir = cityScrap.getDownloadDir(f);
            Sheet data = wb.createSheet("Data");
            int i = 0;
            Row header = data.createRow(i++);
            List<String> columns = TEMPLATE_COLUMNS.get(f.getTemplate());
            for (int j = 0; j < columns.size(); j++) {
                header.createCell(j).setCellValue(columns.get(j));
            }
            for (Notice n : f.getNotices()) {
                List<String> authors = n.getAuthors();
                // Upload only files where all author(s) are known and in public domain
                if (CollectionUtils.isNotEmpty(authors)) {
                    List<Author> wikiAuthors = authors.stream()
                        .map(a -> WikidataUtils.retrieveAuthorInfo(a, cityScrap.getPredefinedAuthors())).toList();
                    if (wikiAuthors.stream().allMatch(a -> a != null && Boolean.TRUE.equals(a.isPublicDomain()))) {
                        Row row = data.createRow(i++);
                        createCell(row, columns, "path", () -> downloadDir.resolve(n.getFilename()).toAbsolutePath());
                        createCell(row, columns, "name", () -> String.format("%s (%s)", n.getTitle().replace("[", "").replace("]", ""), n.getCote()));
                        createCell(row, columns, "photographer", () -> wikiAuthors.stream().map(a -> "{{"
                                + (a.getCommonsCreator() != null ? ("Creator:" + a.getCommonsCreator()) : ("Institution:" + a.getCommonsInstitution()))
                                + "}}").collect(joining("\n")));
                        createCell(row, columns, "title", () -> String.format("{{fr|''%s.''}}", n.getTitle()));
                        createCell(row, columns, "description", () -> n.getDescription().isBlank() ? null : String.format("{{fr|''%s''}}", n.getDescription()));
                        createCell(row, columns, "date", n::getDate);
                        createCell(row, columns, "institution", () -> String.format("{{Institution:%s}}", cityScrap.getInstitution()));
                        createCell(row, columns, "accession_number", n::getCote);
                        createCell(row, columns, "source", n::getUrl);
                        createCell(row, columns, "permission", () -> "{{Template:PD-France}}");
                        createCell(row, columns, "other_fields", () -> cityScrap.getOtherFields(n));
                        createCell(row, columns, "categories", () -> cityScrap.getCategories(n).stream().filter(Objects::nonNull).collect(joining(";")));
                    }
                }
            }
            Sheet template = wb.createSheet("Template");
            template.createRow(0).createCell(0).setCellValue("'" + getTemplateContents(f.getTemplate()));
            wb.write(out);
        }
    }

    private static void createCell(Row row, List<String> columns, String column, Supplier<Object> value) {
        int idx = columns.indexOf(column);
        if (idx > -1) {
            row.createCell(idx).setCellValue(value.get() != null ? value.get().toString() : null);
        }
    }
}
