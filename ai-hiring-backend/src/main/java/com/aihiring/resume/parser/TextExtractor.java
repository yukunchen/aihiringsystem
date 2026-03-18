package com.aihiring.resume.parser;

import java.io.IOException;
import java.io.InputStream;

public interface TextExtractor {
    String extract(InputStream inputStream) throws IOException;
}
