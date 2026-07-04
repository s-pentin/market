package org.market.app.controllers;

import org.market.app.dto.ProductsPage;
import org.market.app.models.Action;
import org.market.app.models.SortType;
import org.market.app.services.CartService;
import org.market.app.services.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProductController {

    private final ProductService productService;
    private final CartService cartService;

    public ProductController(ProductService productService, CartService cartService) {
        this.productService = productService;
        this.cartService = cartService;
    }

    @GetMapping({"/", "/items"})
    public String getProducts(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "NO") SortType sort,
            @RequestParam(defaultValue = "1") Integer pageNumber,
            @RequestParam(defaultValue = "5") Integer pageSize,
            Model model) {

        ProductsPage page = productService.getProducts(search, sort, pageNumber, pageSize);

        model.addAttribute("items", page.getItems());
        model.addAttribute("search", page.getSearch());
        model.addAttribute("sort", page.getSort());
        model.addAttribute("paging", page.getPaging());

        return "items";
    }

    @GetMapping("/items/{id}")
    public String getProductById(@PathVariable Long id, Model model) {
        model.addAttribute("item", productService.getProductById(id));
        return "item";
    }

    @PostMapping("/items")
    public String updateProductCountFromItems(
            @RequestParam Long id,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "NO") SortType sort,
            @RequestParam(defaultValue = "1") Integer pageNumber,
            @RequestParam(defaultValue = "5") Integer pageSize,
            @RequestParam Action action,
            RedirectAttributes redirectAttributes) {

        cartService.changeCount(id, action);

        if (search != null) {
            redirectAttributes.addAttribute("search", search);
        }
        redirectAttributes.addAttribute("sort", sort);
        redirectAttributes.addAttribute("pageNumber", pageNumber);
        redirectAttributes.addAttribute("pageSize", pageSize);

        return "redirect:/items";
    }

    @PostMapping("/items/{id}")
    public String updateProductCountFromItem(
            @PathVariable Long id,
            @RequestParam Action action,
            Model model) {

        cartService.changeCount(id, action);
        model.addAttribute("item", productService.getProductById(id));
        return "item";
    }
}
