package kitchenpos.dto;

public class ProductResponse {

    private final Long id;
    private final String name;
    private final Long price;

    public ProductResponse(Long id, String name, Long price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getPrice() {
        return price;
    }
}
