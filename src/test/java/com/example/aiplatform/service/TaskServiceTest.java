package com.example.aiplatform.service;

import com.example.aiplatform.entity.ImageTask;
import com.example.aiplatform.mapper.ImageTaskMapper;
import com.example.aiplatform.task.TaskStatus;
import com.example.aiplatform.ws.TaskStatusBroadcaster;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TaskServiceTest {

    @Mock
    private ImageTaskMapper imageTaskMapper;

    @Mock
    private TaskStatusBroadcaster broadcaster;

    @InjectMocks
    private ImageTaskService imageTaskService;

    @Test
    void createTask_should_be_pending_and_push_event() {
        when(imageTaskMapper.insert(any(ImageTask.class))).thenAnswer(inv -> {
            ImageTask t = inv.getArgument(0);
            t.setId(1L);
            return 1;
        });

        ImageTask t = imageTaskService.createTask(10L, "a cat", null);
        Assertions.assertEquals(TaskStatus.PENDING.name(), t.getStatus());
        verify(broadcaster, times(1)).publish(eq(1L), eq(10L), eq(TaskStatus.PENDING), any());
    }

    @Test
    void status_flow_pending_to_processing_ok() {
        ImageTask t = new ImageTask();
        t.setId(1L);
        t.setUserId(10L);
        t.setStatus(TaskStatus.PENDING.name());

        when(imageTaskMapper.selectById(1L)).thenReturn(t);

        imageTaskService.markProcessing(1L);
        Assertions.assertEquals(TaskStatus.PROCESSING.name(), t.getStatus());
        verify(imageTaskMapper, times(1)).updateById(t);
        verify(broadcaster, times(1)).publish(eq(1L), eq(10L), eq(TaskStatus.PROCESSING), any());
    }

    @Test
    void status_flow_processing_to_done_ok() {
        ImageTask t = new ImageTask();
        t.setId(2L);
        t.setUserId(10L);
        t.setStatus(TaskStatus.PROCESSING.name());

        when(imageTaskMapper.selectById(2L)).thenReturn(t);

        imageTaskService.markDone(2L, "http://img", "C:/a.png");
        Assertions.assertEquals(TaskStatus.DONE.name(), t.getStatus());
        verify(broadcaster, times(1)).publish(eq(2L), eq(10L), eq(TaskStatus.DONE), any());
    }

    @Test
    void status_flow_processing_to_failed_ok() {
        ImageTask t = new ImageTask();
        t.setId(3L);
        t.setUserId(10L);
        t.setStatus(TaskStatus.PROCESSING.name());

        when(imageTaskMapper.selectById(3L)).thenReturn(t);

        imageTaskService.markFailed(3L, "boom");
        Assertions.assertEquals(TaskStatus.FAILED.name(), t.getStatus());
        verify(broadcaster, times(1)).publish(eq(3L), eq(10L), eq(TaskStatus.FAILED), eq("boom"));
    }

    @Test
    void invalid_transition_pending_to_done_should_throw() {
        ImageTask t = new ImageTask();
        t.setId(4L);
        t.setUserId(10L);
        t.setStatus(TaskStatus.PENDING.name());

        when(imageTaskMapper.selectById(4L)).thenReturn(t);

        Assertions.assertThrows(IllegalStateException.class,
                () -> imageTaskService.markDone(4L, null, null));
    }
}
