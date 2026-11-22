package ru.practicum.dto.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NewEventDto {

    private static final int MIN_ANNOTATION_LENGTH = 20;
    private static final int MAX_ANNOTATION_LENGTH = 2000;
    private static final int MIN_DESCRIPTION_LENGTH = 20;
    private static final int MAX_DESCRIPTION_LENGTH = 7000;
    private static final int MIN_TITLE_LENGTH = 3;
    private static final int MAX_TITLE_LENGTH = 120;

    @NotBlank(message = "Описание события не может быть пустым")
    @Size(min = MIN_ANNOTATION_LENGTH, max = MAX_ANNOTATION_LENGTH,
            message = "Длина описания должна быть от 20 до 2000 символов")
    private String annotation;

    @NotNull(message = "id категории не может быть пустым")
    private Long category;

    @NotBlank(message = "Полное описание не может быть пустым")
    @Size(min = MIN_DESCRIPTION_LENGTH, max = MAX_DESCRIPTION_LENGTH,
            message = "Полное описание должно быть от 20 до 7000 символов")
    private String description;

    @NotNull(message = "Время события не может быть пустым")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventDate;

    @NotNull(message = "Место события не может быть пустым")
    @Valid
    private Location location;

    private boolean paid;

    @PositiveOrZero(message = "Количество участников не может быть меньше нуля")
    private int participantLimit;

    private Boolean requestModeration;

    @NotBlank(message = "Заголовок события не может быть пустым")
    @Size(min = MIN_TITLE_LENGTH, max = MAX_TITLE_LENGTH,
            message = "Заголовок события должен быть от 3 до 120 символов")
    private String title;
}
