
package ru.practicum.dto.compilation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NewCompilationDto {

    private static final int TITLE_MIN_LENGTH = 1;
    private static final int TITLE_MAX_LENGTH = 50;

    private Set<Long> events;

    private boolean pinned;

    @NotBlank(message = "Заголовок не может быть пустым")
    @Size(
            min = TITLE_MIN_LENGTH,
            max = TITLE_MAX_LENGTH,
            message = "Длина заголовка должна быть от 1 до 50 символов"
    )
    private String title;
}
