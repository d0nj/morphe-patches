package app.d0nj.extension.niagara;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Encoding helpers shared by the weather provider adapters. The output shape is
 * the OpenWeatherMap One Call payload Niagara Launcher already deserializes.
 */
final class NiagaraWeather {

    private NiagaraWeather() {
    }

    static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * Reads latitude and longitude from a Niagara coordinates object. Field
     * names are matched first because {@code getDeclaredFields()} order is
     * unspecified and varies between runs; the +/-90 bound is only a fallback
     * for obfuscated fields, and it cannot tell the pair apart when both
     * values fall inside it.
     */
    static float[] latLonOf(Object coordinates) throws Exception {
        Field[] floats = floatFields(coordinates.getClass());
        if (floats.length < 2) {
            return null;
        }
        Field latitude = null;
        Field longitude = null;
        for (Field field : floats) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            if (latitude == null && name.startsWith("lat")) {
                latitude = field;
            } else if (longitude == null && name.startsWith("lon")) {
                longitude = field;
            }
        }
        if (latitude != null && longitude != null) {
            return new float[] { latitude.getFloat(coordinates), longitude.getFloat(coordinates) };
        }
        float first = floats[0].getFloat(coordinates);
        float second = floats[1].getFloat(coordinates);
        return Math.abs(first) > 90f
            ? new float[] { second, first }
            : new float[] { first, second };
    }

    static Field[] floatFields(Class<?> type) {
        List<Field> floats = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (field.getType() != float.class) {
                continue;
            }
            field.setAccessible(true);
            floats.add(field);
        }
        return floats.toArray(new Field[0]);
    }

    static JSONArray conditions(int owmId, String description, boolean day) throws Exception {
        return new JSONArray().put(new JSONObject()
            .put("id", owmId)
            .put("description", description)
            .put("icon", day ? "d" : "n"));
    }

    static String payload(String provider, String lat, String lon, JSONObject current,
                          JSONArray hourly, JSONArray daily) throws Exception {
        JSONObject root = new JSONObject();
        root.put("timestamp", Instant.now().getEpochSecond());
        root.put("provider", provider);
        root.put("lat", lat);
        root.put("long", lon);
        root.put("lang", "en");
        root.put("forecast", new JSONObject()
            .put("current", current)
            .put("minutely", new JSONArray())
            .put("hourly", hourly)
            .put("daily", daily));
        return root.toString();
    }
}
