package com.smhrd.web.dto;

import lombok.Data;

import java.util.List;

@Data
public class FolderTreeDto {
    private Long folderId;
    private String folderName;
    private Long parentFolderId;
    private Integer sortOrder;
    private List<FolderTreeDto> children;
    private List<NoteSimpleDto> notes;
}
