package p1.utils;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Converter
public class LongListConverter implements AttributeConverter<List<Long>, String> {
    @Override
    public String convertToDatabaseColumn(List<Long> longs) {
        return longs.stream().map(Object::toString).collect(Collectors.joining(", "));
    }

    @Override
    public List<Long> convertToEntityAttribute(String s) {
        if (s == null || s.trim().isEmpty()) return new ArrayList<>();
        return Arrays.stream(s.split(",")).map(Long::parseLong).collect(Collectors.toList());
    }
}
