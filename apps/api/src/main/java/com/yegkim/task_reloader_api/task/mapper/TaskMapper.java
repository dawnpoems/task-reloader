package com.yegkim.task_reloader_api.task.mapper;

import com.yegkim.task_reloader_api.task.dto.CreateTaskRequest;
import com.yegkim.task_reloader_api.task.dto.TaskResponse;
import com.yegkim.task_reloader_api.task.entity.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TaskMapper {

    TaskResponse toResponse(Task task);

    List<TaskResponse> toResponseList(List<Task> tasks);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "timezone", constant = "Asia/Seoul")
    @Mapping(target = "nextDueAt", ignore = true)
    @Mapping(target = "lastCompletedAt", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Task toEntity(CreateTaskRequest request);
}

