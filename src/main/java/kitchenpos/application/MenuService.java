package kitchenpos.application;

import kitchenpos.dao.*;
import kitchenpos.domain.history.MenuHistory;
import kitchenpos.domain.menu.Menu;
import kitchenpos.domain.menu.MenuProduct;
import kitchenpos.domain.menu.MenuProducts;
import kitchenpos.domain.product.Product;
import kitchenpos.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MenuService {
    private final MenuDao menuDao;
    private final MenuGroupDao menuGroupDao;
    private final MenuProductDao menuProductDao;
    private final ProductDao productDao;
    private final MenuHistoryDao menuHistoryDao;

    public MenuService(
            final MenuDao menuDao,
            final MenuGroupDao menuGroupDao,
            final MenuProductDao menuProductDao,
            final ProductDao productDao,
            final MenuHistoryDao menuHistoryDao
            ) {
        this.menuDao = menuDao;
        this.menuGroupDao = menuGroupDao;
        this.menuProductDao = menuProductDao;
        this.productDao = productDao;
        this.menuHistoryDao = menuHistoryDao;
    }

    @Transactional
    public MenuResponse create(final MenuCreateRequest menuCreateRequest) {
        MenuProducts menuProducts = generateMenuProducts(menuCreateRequest);
        Menu menu = menuDao.save(generateMenu(menuCreateRequest, menuProducts));

        final Long menuId = menu.getId();
        List<MenuProduct> results = menuProducts.changeMenuId(menuId).getMenuProducts();
        results = results.stream()
                .map(menuProductDao::save)
                .collect(Collectors.toUnmodifiableList());
        menu = menu.changeMenuProducts(results);
        List<MenuProductResponse> menuProductResponses = menu.getMenuProducts().stream()
                .map(each -> new MenuProductResponse(each.getSeq(), each.getMenuId(), each.getProductId(), each.getQuantity()))
                .collect(Collectors.toUnmodifiableList());
        menuHistoryDao.save(new MenuHistory(menu.getId(), menu.getPrice(), menu.getName(), LocalDateTime.now()));
        return new MenuResponse(menu.getId(), menu.getName(), menu.getPrice(), menu.getMenuGroupId(), menuProductResponses);
    }

    public List<MenuResponse> list() {
        List<Menu> menus = menuDao.findAll();
        List<Menu> fullMenus = new ArrayList<>();
        for (Menu menu : menus) {
            List<MenuProduct> menuProducts = menuProductDao.findAllByMenuId(menu.getId());
            fullMenus.add(menu.changeMenuProducts(menuProducts));
        }

        List<MenuResponse> menuResponses = new ArrayList<>();
        for (Menu menu : fullMenus) {
            List<MenuProductResponse> menuProductResponses = menu.getMenuProducts().stream()
                    .map(each -> new MenuProductResponse(each.getSeq(), each.getMenuId(), each.getProductId(), each.getQuantity()))
                    .collect(Collectors.toUnmodifiableList());
            menuResponses.add(new MenuResponse(menu.getId(), menu.getName(), menu.getPrice(), menu.getMenuGroupId(), menuProductResponses));
        }
        return menuResponses;
    }

    private void validateExistMenuGroup(Long menuGroupId) {
        if (!menuGroupDao.existsById(menuGroupId)) {
            throw new IllegalArgumentException();
        }
    }

    private MenuProducts generateMenuProducts(MenuCreateRequest menuCreateRequest) {
        validateExistProduct(menuCreateRequest);
        Map<Long, Long> groupByMenuProductsId = menuCreateRequest.getMenuProducts().stream()
                .collect(Collectors.toMap(MenuProductsRequest::getProductId, MenuProductsRequest::getQuantity));

        List<MenuProduct> savedMenuProducts = groupByMenuProductsId.keySet()
                .stream()
                .map(each -> new MenuProduct(each, groupByMenuProductsId.get(each)))
                .collect(Collectors.toUnmodifiableList());

        return new MenuProducts(savedMenuProducts);
    }

    private void validateExistProduct(MenuCreateRequest menuCreateRequest) {
        List<Long> existProductIds = productDao.findAll().stream()
                .map(Product::getId)
                .collect(Collectors.toUnmodifiableList());
        boolean isNotExistProductId = menuCreateRequest.getMenuProducts().stream()
                .anyMatch(each -> !existProductIds.contains(each.getProductId()));
        if (isNotExistProductId) {
            throw new IllegalArgumentException();
        }
    }

    private Menu generateMenu(MenuCreateRequest menuCreateRequest, MenuProducts menuProducts) {
        validateExistMenuGroup(menuCreateRequest.getMenuGroupId());
        validateOverTotalPrice(menuCreateRequest, menuProducts);
        return new Menu(menuCreateRequest.getName(), menuCreateRequest.getPrice(), menuCreateRequest.getMenuGroupId());
    }

    private void validateOverTotalPrice(MenuCreateRequest menuCreateRequest, MenuProducts menuProducts) {
        if (menuCreateRequest.getPrice() == null) {
            throw new IllegalArgumentException();
        }
        Map<Long, Long> groupedPriceByProductId = productDao.findAllByIds(
                        menuProducts.getProductsIds())
                .stream()
                .collect(Collectors.toMap(Product::getId, Product::getPrice));
        if (menuProducts.isOverThanTotalPrice(groupedPriceByProductId, menuCreateRequest.getPrice())) {
            throw new IllegalArgumentException();
        }
    }

    public void changMenuPrice(MenuPriceChangeRequest menuPriceChangeRequest) {
        Optional<Menu> searchedMenu = menuDao.findById(menuPriceChangeRequest.getMenuId());
        if (searchedMenu.isEmpty()) {
            throw new IllegalArgumentException();
        }
        Menu menu = searchedMenu.get();
        menu = menu.changePrice(menuPriceChangeRequest.getNewPrice());
        menuDao.save(menu);
        menuHistoryDao.save(new MenuHistory(menu.getId(), menu.getPrice(), menu.getName(), LocalDateTime.now()));
    }

    public void changMenuName(MenuNameChangeRequest menuNameChangeRequest) {
        Optional<Menu> searchedMenu = menuDao.findById(menuNameChangeRequest.getMenuId());
        if (searchedMenu.isEmpty()) {
            throw new IllegalArgumentException();
        }
        Menu menu = searchedMenu.get();
        menu = menu.changeName(menuNameChangeRequest.getNewName());
        menuDao.save(menu);
        menuHistoryDao.save(new MenuHistory(menu.getId(), menu.getPrice(), menu.getName(), LocalDateTime.now()));
    }
}
