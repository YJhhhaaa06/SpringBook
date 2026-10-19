package io.github.yjhhhaaa06.springbook.book.controller;

import io.github.yjhhhaaa06.springbook.book.dto.BookCopiesRequest;
import io.github.yjhhhaaa06.springbook.book.dto.BookCreateRequest;
import io.github.yjhhhaaa06.springbook.book.dto.BookResponse;
import io.github.yjhhhaaa06.springbook.book.dto.BookUpdateRequest;
import io.github.yjhhhaaa06.springbook.book.service.BookService;
import io.github.yjhhhaaa06.springbook.common.PageResult;
import io.github.yjhhhaaa06.springbook.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图书接口。写操作（新增/修改/删除/入库下架）要求 ADMIN，查询要求已认证。
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping
    public Result<BookResponse> create(@Valid @RequestBody BookCreateRequest request) {
        return Result.ok(bookService.create(request));
    }

    @PutMapping("/{id}")
    public Result<BookResponse> update(@PathVariable Long id, @Valid @RequestBody BookUpdateRequest request) {
        return Result.ok(bookService.update(id, request));
    }

    @PatchMapping("/{id}/copies")
    public Result<BookResponse> adjustCopies(@PathVariable Long id,
                                             @Valid @RequestBody BookCopiesRequest request) {
        return Result.ok(bookService.adjustCopies(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bookService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<BookResponse> get(@PathVariable Long id) {
        return Result.ok(bookService.getById(id));
    }

    @GetMapping
    public Result<PageResult<BookResponse>> page(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return Result.ok(bookService.page(keyword, page, size));
    }
}
