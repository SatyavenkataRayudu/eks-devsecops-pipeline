package com.company.booksapi.controller;

import com.company.booksapi.model.Book;
import com.company.booksapi.service.BookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BooksController.class)
class BooksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookService bookService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllBooks_ShouldReturnListOfBooks() throws Exception {
        // Given
        Book book1 = new Book(1L, "Test Book 1", "Author 1", "123456789", 29.99, "Description 1");
        Book book2 = new Book(2L, "Test Book 2", "Author 2", "987654321", 39.99, "Description 2");
        when(bookService.getAllBooks()).thenReturn(Arrays.asList(book1, book2));

        // When & Then
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Test Book 1"))
                .andExpect(jsonPath("$[1].title").value("Test Book 2"));
    }

    @Test
    void getBookById_WhenBookExists_ShouldReturnBook() throws Exception {
        // Given
        Book book = new Book(1L, "Test Book", "Test Author", "123456789", 29.99, "Test Description");
        when(bookService.getBookById(1L)).thenReturn(Optional.of(book));

        // When & Then
        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Test Book"));
    }

    @Test
    void getBookById_WhenBookNotExists_ShouldReturnNotFound() throws Exception {
        // Given
        when(bookService.getBookById(anyLong())).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/books/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createBook_WithValidData_ShouldReturnCreatedBook() throws Exception {
        // Given
        Book inputBook = new Book(null, "New Book", "New Author", "123456789", 29.99, "New Description");
        Book createdBook = new Book(1L, "New Book", "New Author", "123456789", 29.99, "New Description");
        when(bookService.createBook(any(Book.class))).thenReturn(createdBook);

        // When & Then
        mockMvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inputBook)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("New Book"));
    }

    @Test
    void health_ShouldReturnHealthyMessage() throws Exception {
        mockMvc.perform(get("/api/books/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Books API is healthy!"));
    }
}