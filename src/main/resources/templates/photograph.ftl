=={{int:filedesc}}==
{{Photograph
 |photographer = ${photographer}
 |title = ${title}
 |description = ${description}
 |depicted people = ${depicted_people}
 |depicted place = ${depicted_place}
 |date = ${date}
 |medium = ${medium}
 |dimensions = ${dimensions}
 |institution = ${institution}
 |department = ${department}
 |references = ${references}
 |object history = ${object_history}
 |exhibition history = ${exhibition_history}
 |credit line = ${credit_line}
 |inscriptions = ${inscriptions}
 |notes = ${notes}
 |accession number = ${accession_number}
 |source = ${source}
 |permission = ${permission}
 |other_versions = ${other_versions}
 |other_fields = ${other_fields}
}}

=={{int:license-header}}==
${license}${partnership}

<#if categories ? has_content>
<#list categories ? split(";") as category>
[[Category:${category?trim}]]
</#list>
<#else>{{subst:unc}}
</#if>