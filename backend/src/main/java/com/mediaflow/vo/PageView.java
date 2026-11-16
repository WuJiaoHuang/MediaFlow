package com.mediaflow.vo;

import java.util.List;

public record PageView<T>(List<T> records, long total, long page, long size) {}
