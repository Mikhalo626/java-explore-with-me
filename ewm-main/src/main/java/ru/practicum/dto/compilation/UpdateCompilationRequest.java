
package ru.practicum.dto.compilation;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateCompilationRequest {

    private static final String TITLE_SIZE_MESSAGE =
            "Длина заголовка должна быть от 1 до 50 символов";

    private static final int MIN_TITLE_LENGTH = 1;
    private static final int MAX_TITLE_LENGTH = 50;

    private Set<Long> events;

    private Boolean pinned;

    @Size(
            min = MIN_TITLE_LENGTH,
            max = MAX_TITLE_LENGTH,
            message = TITLE_SIZE_MESSAGE
    )
    private String title;
}