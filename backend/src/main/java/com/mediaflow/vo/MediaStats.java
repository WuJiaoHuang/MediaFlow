package com.mediaflow.vo;

import java.util.List;

public class MediaStats {
  public long total;
  public long videos;
  public long audios;
  public long images;
  public long recent;
  public long totalBytes;
  public List<CategoryCount> categories;

  public static class CategoryCount {
    public Long categoryId;
    public String categoryName;
    public long count;
  }
}
