package dev.jasper.sdk.events;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A named, typed channel. The id's namespace decides who may publish: {@code jasper.*} belongs to the
 * application and {@code <plugin id>.*} to that plugin. Two topics are the same when both the id and
 * the payload type match; the runtime rejects one id used with two payload types.
 *
 * @param <T> payload type, normally an immutable record
 */
public final class Topic<T> {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_.-]{0,127}");
    private final String id;
    private final Class<T> payloadType;

    private Topic(String id, Class<T> payloadType) {
        this.id = id;
        this.payloadType = payloadType;
    }

    /**
     * Creates a topic constant.
     *
     * @param id namespaced id matching {@code [a-z][a-z0-9_.-]{0,127}}
     * @param payloadType the exact payload class
     * @param <T> payload type
     * @return the topic
     */
    public static <T> Topic<T> of(String id, Class<T> payloadType) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(payloadType, "payloadType");
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid topic id: " + id);
        return new Topic<>(id, payloadType);
    }

    /**
     * The namespaced id.
     *
     * @return the id
     */
    public String id() { return id; }

    /**
     * The payload class.
     *
     * @return the payload class
     */
    public Class<T> payloadType() { return payloadType; }

    @Override public boolean equals(Object other) {
        return other instanceof Topic<?> topic && id.equals(topic.id) && payloadType == topic.payloadType;
    }

    @Override public int hashCode() { return Objects.hash(id, payloadType); }

    @Override public String toString() { return "Topic[" + id + ", " + payloadType.getName() + "]"; }
}
