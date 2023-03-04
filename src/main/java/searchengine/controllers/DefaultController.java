package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import searchengine.services.IndexingService;
import searchengine.services.IndexingServiceImpl;

@Controller
@RequiredArgsConstructor
public class DefaultController {

    private final IndexingService indexingService;

    /**
     * Метод формирует страницу из HTML-файла index.html,
     * который находится в папке resources/templates.
     * Это делает библиотека Thymeleaf.
     */
    @RequestMapping("/")
    public String index() {
        return "index";
    }
}
