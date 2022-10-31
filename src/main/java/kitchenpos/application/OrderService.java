package kitchenpos.application;

import kitchenpos.dao.MenuDao;
import kitchenpos.dao.OrderDao;
import kitchenpos.dao.OrderLineItemDao;
import kitchenpos.dao.OrderTableDao;
import kitchenpos.domain.*;
import kitchenpos.dto.*;
import org.aspectj.weaver.ast.Or;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private final MenuDao menuDao;
    private final OrderDao orderDao;
    private final OrderLineItemDao orderLineItemDao;
    private final OrderTableDao orderTableDao;

    public OrderService(
            final MenuDao menuDao,
            final OrderDao orderDao,
            final OrderLineItemDao orderLineItemDao,
            final OrderTableDao orderTableDao
    ) {
        this.menuDao = menuDao;
        this.orderDao = orderDao;
        this.orderLineItemDao = orderLineItemDao;
        this.orderTableDao = orderTableDao;
    }

    @Transactional
    public OrderResponse create(OrderCreateRequest orderCreateRequest) {
        OrderLineItems orderLineItems = generateOrderLineItems(orderCreateRequest);
        OrderTable orderTable = generateOrderTable(orderCreateRequest);

        Order savedOrder = orderDao.save(generateOrder(orderTable));

        final Long orderId = savedOrder.getId();
        List<OrderLineItem> lastOrderLineItems = orderLineItems.changeOrderId(orderId).getOrderLineItems();
        lastOrderLineItems = lastOrderLineItems.stream()
                .map(orderLineItemDao::save)
                .collect(Collectors.toUnmodifiableList());
        savedOrder = savedOrder.changeOrderLineItems(lastOrderLineItems);
        List<OrderLineItemResponse> orderLineItemResponses = savedOrder.getOrderLineItems().stream()
                .map(each -> new OrderLineItemResponse(each.getSeq(), each.getOrderId(), each.getMenuId(), each.getQuantity()))
                .collect(Collectors.toUnmodifiableList());
        return new OrderResponse(savedOrder.getId(), savedOrder.getOrderTableId(), savedOrder.getOrderStatus().name(),
                savedOrder.getOrderedTime(), orderLineItemResponses, savedOrder.isCompletion());
    }

    private OrderLineItems generateOrderLineItems(OrderCreateRequest orderCreateRequest) {
        validateExistMenu(orderCreateRequest);
        Map<Long, Long> groupByMenuId = orderCreateRequest.getOrderLineItems().stream()
                .collect(Collectors.toMap(OrderLineItemRequest::getMenuId, OrderLineItemRequest::getQuantity));

        List<OrderLineItem> savedOrderLineItems = groupByMenuId.keySet().stream()
                .map(each -> new OrderLineItem(each, groupByMenuId.get(each)))
                .collect(Collectors.toUnmodifiableList());

        return new OrderLineItems(savedOrderLineItems);
    }

    private void validateExistMenu(OrderCreateRequest orderCreateRequest) {
        List<Long> existMenuIds = menuDao.findAll().stream()
                .map(Menu::getId)
                .collect(Collectors.toUnmodifiableList());
        boolean isNotExistMenuId = orderCreateRequest.getOrderLineItems().stream()
                .anyMatch(each -> !existMenuIds.contains(each.getMenuId()));
        if (isNotExistMenuId) {
            throw new IllegalArgumentException();
        }
    }

    private OrderTable generateOrderTable(OrderCreateRequest orderCreateRequest) {
        OrderTable orderTable = orderTableDao.findById(orderCreateRequest.getOrderTableId())
                .orElseThrow(IllegalArgumentException::new);
        validateNonEmptyOrderTable(orderTable);
        return orderTable;
    }

    private void validateNonEmptyOrderTable(OrderTable orderTable) {
        if (orderTable.isEmpty()) {
            throw new IllegalArgumentException();
        }
    }

    private Order generateOrder(OrderTable orderTable) {
        return new Order(orderTable.getId(), OrderStatus.COOKING, LocalDateTime.now());
    }

    public List<OrderResponse> list() {
        List<Order> orders = orderDao.findAll();
        List<Order> fullOrders = new ArrayList<>();
        for (Order order : orders) {
            List<OrderLineItem> orderLineItems = orderLineItemDao.findAllByOrderId(order.getId());
            fullOrders.add(order.changeOrderLineItems(orderLineItems));
        }

        List<OrderResponse> orderResponses = new ArrayList<>();
        for (Order order : fullOrders) {
            List<OrderLineItemResponse> orderLineItemResponses = order.getOrderLineItems().stream()
                    .map(each -> new OrderLineItemResponse(each.getSeq(), each.getOrderId(), each.getMenuId(), each.getQuantity()))
                    .collect(Collectors.toUnmodifiableList());
            orderResponses.add(new OrderResponse(order.getId(), order.getOrderTableId(), order.getOrderStatus().name(),
                    order.getOrderedTime(), orderLineItemResponses, order.isCompletion()));
        }
        return orderResponses;
    }

    @Transactional
    public OrderResponse changeOrderStatus(final Long orderId, OrderStatusUpdateRequest orderStatusUpdateRequest) {
        Order searchedOrder = searchOrder(orderId);
        final OrderStatus orderStatus = OrderStatus.valueOf(orderStatusUpdateRequest.getOrderStatus());

        searchedOrder = orderDao.save(searchedOrder.changeOrderStatus(orderStatus));

        searchedOrder = searchedOrder.changeOrderLineItems(orderLineItemDao.findAllByOrderId(orderId));
        List<OrderLineItemResponse> orderLineItemResponses = searchedOrder.getOrderLineItems().stream()
                .map(each -> new OrderLineItemResponse(each.getSeq(), each.getOrderId(), each.getMenuId(), each.getQuantity()))
                .collect(Collectors.toUnmodifiableList());
        return new OrderResponse(searchedOrder.getId(), searchedOrder.getOrderTableId(), searchedOrder.getOrderStatus().name(),
                searchedOrder.getOrderedTime(), orderLineItemResponses, searchedOrder.isCompletion());
    }

    private Order searchOrder(Long orderId) {
        final Order savedOrder = orderDao.findById(orderId)
                .orElseThrow(IllegalArgumentException::new);

        validateCompletedOrder(savedOrder);
        return savedOrder;
    }

    private void validateCompletedOrder(Order order) {
        if (order.isCompletion()) {
            throw new IllegalArgumentException();
        }
    }
}
