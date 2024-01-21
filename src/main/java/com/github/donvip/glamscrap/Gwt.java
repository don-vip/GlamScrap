package com.github.donvip.glamscrap;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.lang3.ArrayUtils;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

public class Gwt {

    public static final String BOOLEAN = "java.lang.Boolean/476441737";
    public static final String INTEGER = "java.lang.Integer/3438268394";
    public static final String STRING = "java.lang.String/2004016611";

    private static final ScriptEngine NASHORN = new ScriptEngineManager().getEngineByName("nashorn");

    public record TypedValue(String declaredType, String runtimeType, String stringValue, int intValue, List<TypedValue> fieldsInAlphabeticalOrder) {
    }

    public record GwtResponse(int protocolVersion, int flags, List<String> strings, List<Object> values) {
    }

    public static HttpRequest request(String uri, String baseUrl, String strongNamePolicyFile, String permutation, String service, String method, List<TypedValue> argTypes) {
        return requestBuilder(uri, baseUrl, strongNamePolicyFile, permutation, service, method, argTypes).build();
    }

    public static Builder requestBuilder(String uri, String baseUrl, String strongNamePolicyFile, String permutation, String service, String method, List<TypedValue> argTypes) {
        return HttpRequest.newBuilder()
            .headers("Content-Type", "text/x-gwt-rpc; charset=utf-8", "X-GWT-Module-Base", baseUrl, "X-GWT-Permutation", permutation)
            .method("POST", BodyPublishers.ofString(requestPayload(baseUrl, strongNamePolicyFile, service, method, argTypes)))
            .uri(URI.create(uri));
    }

    public static String requestPayload(String baseUrl, String strongNamePolicyFile, String service, String method, List<TypedValue> typedValues) {
        List<String> strings = new ArrayList<>();
        strings.add(requireNonNull(baseUrl));
        strings.add(requireNonNull(strongNamePolicyFile));
        strings.add(requireNonNull(service));
        strings.add(requireNonNull(method));
        for (TypedValue typedVal : typedValues) {
            addStrings(strings, typedVal);
        }

        List<Integer> ints = new ArrayList<>();
        ints.add(1); // baseUrl
        ints.add(2); // strongNamePolicyFile
        ints.add(3); // service
        ints.add(4); // method
        ints.add(typedValues.size()); // number of arguments
        for (TypedValue typedVal : typedValues) {
            ints.add(strings.indexOf(typedVal.declaredType) + 1);
        }
        for (TypedValue typedVal : typedValues) {
            addValueIndice(strings, ints, typedVal);
        }

        return requestPayload(strings.toArray(new String[0]), ArrayUtils.toPrimitive(ints.toArray(new Integer[0])));
    }

    private static void addStrings(List<String> strings, TypedValue typedVal) {
        if (typedVal.declaredType != null && !strings.contains(typedVal.declaredType)) {
            strings.add(typedVal.declaredType);
        }
        if (typedVal.runtimeType != null && !strings.contains(typedVal.runtimeType)) {
            strings.add(typedVal.runtimeType);
        }
        if (typedVal.stringValue != null && !strings.contains(typedVal.stringValue)) {
            strings.add(typedVal.stringValue);
        }
        for (TypedValue field : typedVal.fieldsInAlphabeticalOrder) {
            addStrings(strings, requireNonNull(field));
        }
    }

    private static void addValueIndice(List<String> strings, List<Integer> ints, TypedValue typedVal) {
        if (typedVal.runtimeType != null ) {
            ints.add(strings.indexOf(typedVal.runtimeType) + 1);
        }
        if (typedVal.fieldsInAlphabeticalOrder.isEmpty()) {
            if (typedVal.declaredType != null && typedVal.declaredType.startsWith("java.lang.String")) {
                ints.add(typedVal.stringValue != null ? strings.indexOf(typedVal.stringValue) + 1 : 0);
            } else if (typedVal.runtimeType != null && (typedVal.runtimeType.startsWith("java.lang.Integer") || typedVal.runtimeType.startsWith("java.lang.Boolean"))) {
                ints.add(typedVal.intValue);
            }
        }
        for (TypedValue field : typedVal.fieldsInAlphabeticalOrder) {
            addValueIndice(strings, ints, field);
        }
    }

    private static String requestPayload(String[] strings, int[] ints) {
        return requestPayload(7, 0, strings, ints);
    }

    private static String requestPayload(int protocolVersion, int flags, String[] strings, int[] ints) {
        return String.format("%d|%d|%d|%s|%s|", protocolVersion, flags, strings.length, String.join("|", strings),
                Arrays.stream(ints).mapToObj(Integer::toString).collect(Collectors.joining("|")));
    }

    public static GwtResponse decodeResponse(String response) throws ScriptException {
        if (!response.startsWith("//OK")) {
            throw new IllegalArgumentException("Invalid response: " + response);
        }
        ScriptObjectMirror res = (ScriptObjectMirror) NASHORN.eval(response.substring(4));
        List<Object> list = new ArrayList<>(res.values());
        Collections.reverse(list);
        return new GwtResponse(
                (Integer) list.get(0),
                (Integer) list.get(1),
                ((ScriptObjectMirror) list.get(2)).values().stream().map(x -> (String) x).toList(),
                list.subList(3, list.size()));
    }
}
