package ru.practicum.dto.category;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCategoryDto {

    private static final int MAX_NAME_LENGTH = 50;
    private static final String NAME_NOT_BLANK_MESSAGE = "Название категории не может быть пустым";
    private static final String NAME_SIZE_MESSAGE = "Название категории не может быть длиннее 50 символов";

    @NotBlank(message = NAME_NOT_BLANK_MESSAGE)
    @Size(max = MAX_NAME_LENGTH, message = NAME_SIZE_MESSAGE)
    private String name;
}
