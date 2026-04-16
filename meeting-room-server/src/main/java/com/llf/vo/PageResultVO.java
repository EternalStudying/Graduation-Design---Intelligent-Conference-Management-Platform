package com.llf.vo;

import lombok.Data;

import java.util.List;

@Data
public class PageResultVO<T> {
    private List<T> list;
    private long total;
    private int pageNum;
    private int pageSize;
}
