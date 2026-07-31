package cn.ahlib.reservation.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.ToNumberPolicy
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.google.gson.TypeAdapter

internal object GsonFactory {
    fun create(): Gson =
        GsonBuilder()
            .registerTypeAdapter(Int::class.javaPrimitiveType, FlexibleIntAdapter)
            .registerTypeAdapter(Int::class.javaObjectType, FlexibleIntAdapter)
            .registerTypeAdapter(Long::class.javaPrimitiveType, FlexibleLongAdapter)
            .registerTypeAdapter(Long::class.javaObjectType, FlexibleLongAdapter)
            .registerTypeAdapter(Double::class.javaPrimitiveType, FlexibleDoubleAdapter)
            .registerTypeAdapter(Double::class.javaObjectType, FlexibleDoubleAdapter)
            .registerTypeAdapter(Boolean::class.javaPrimitiveType, FlexibleBooleanAdapter)
            .registerTypeAdapter(Boolean::class.javaObjectType, FlexibleBooleanAdapter)
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create()
}

private object FlexibleIntAdapter : TypeAdapter<Int>() {
    override fun write(writer: JsonWriter, value: Int?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }

    override fun read(reader: JsonReader): Int? =
        when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }

            JsonToken.NUMBER,
            JsonToken.STRING,
            -> reader.nextString().toDoubleOrNull()?.toInt()
                ?: throw JsonParseException("Expected an integer value")

            JsonToken.BOOLEAN -> if (reader.nextBoolean()) 1 else 0
            else -> throw JsonParseException("Expected an integer value")
        }
}

private object FlexibleLongAdapter : TypeAdapter<Long>() {
    override fun write(writer: JsonWriter, value: Long?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }

    override fun read(reader: JsonReader): Long? =
        when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }

            JsonToken.NUMBER,
            JsonToken.STRING,
            -> reader.nextString().toDoubleOrNull()?.toLong()
                ?: throw JsonParseException("Expected a long value")

            JsonToken.BOOLEAN -> if (reader.nextBoolean()) 1L else 0L
            else -> throw JsonParseException("Expected a long value")
        }
}

private object FlexibleDoubleAdapter : TypeAdapter<Double>() {
    override fun write(writer: JsonWriter, value: Double?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }

    override fun read(reader: JsonReader): Double? =
        when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }

            JsonToken.NUMBER,
            JsonToken.STRING,
            -> reader.nextString().toDoubleOrNull()
                ?: throw JsonParseException("Expected a decimal value")

            JsonToken.BOOLEAN -> if (reader.nextBoolean()) 1.0 else 0.0
            else -> throw JsonParseException("Expected a decimal value")
        }
}

private object FlexibleBooleanAdapter : TypeAdapter<Boolean>() {
    override fun write(writer: JsonWriter, value: Boolean?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }

    override fun read(reader: JsonReader): Boolean? =
        when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }

            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NUMBER,
            JsonToken.STRING,
            -> reader.nextString().let { rawValue ->
                when (rawValue.lowercase()) {
                    "true", "1", "1.0" -> true
                    "false", "0", "0.0" -> false
                    else -> throw JsonParseException("Expected a boolean value")
                }
            }

            else -> throw JsonParseException("Expected a boolean value")
        }
}
