package kitchenpos.application;

import kitchenpos.dao.OrderDao;
import kitchenpos.dao.OrderTableDao;
import kitchenpos.dao.TableGroupDao;
import kitchenpos.domain.order.OrderStatus;
import kitchenpos.domain.table.OrderTable;
import kitchenpos.domain.table.OrderTables;
import kitchenpos.domain.table.TableGroup;
import kitchenpos.dto.OrderTableRequest;
import kitchenpos.dto.OrderTableResponse;
import kitchenpos.dto.TableGroupCreateRequest;
import kitchenpos.dto.TableGroupResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TableGroupService {
    private final OrderDao orderDao;
    private final OrderTableDao orderTableDao;
    private final TableGroupDao tableGroupDao;

    public TableGroupService(final OrderDao orderDao, final OrderTableDao orderTableDao, final TableGroupDao tableGroupDao) {
        this.orderDao = orderDao;
        this.orderTableDao = orderTableDao;
        this.tableGroupDao = tableGroupDao;
    }

    @Transactional
    public TableGroupResponse create(TableGroupCreateRequest tableGroupCreateRequest) {

        OrderTables orderTables = generateOrderTables(tableGroupCreateRequest);
        TableGroup savedTableGroup = tableGroupDao.save(new TableGroup(LocalDateTime.now()));

        final Long tableGroupId = savedTableGroup.getId();
        List<OrderTable> lastOrderTables = orderTables.changeTableGroupId(tableGroupId).getOrderTables();
        lastOrderTables = lastOrderTables.stream()
                .map(orderTableDao::save)
                .collect(Collectors.toUnmodifiableList());

        savedTableGroup = savedTableGroup.changeOrderTables(lastOrderTables);
        List<OrderTableResponse> tableResponses = savedTableGroup.getOrderTables().stream()
                .map(each -> new OrderTableResponse(each.getId(), each.getTableGroupId(), each.getNumberOfGuests(), each.isEmpty()))
                .collect(Collectors.toUnmodifiableList());
        return new TableGroupResponse(savedTableGroup.getId(), savedTableGroup.getCreatedDate(), tableResponses);
    }

    private OrderTables generateOrderTables(TableGroupCreateRequest tableGroupCreateRequest) {
        final List<Long> orderTablesIds = tableGroupCreateRequest.getOrderTables()
                .stream()
                .map(OrderTableRequest::getId)
                .collect(Collectors.toUnmodifiableList());

        List<OrderTable> savedOrderTables = orderTableDao.findAllByIdIn(orderTablesIds);
        OrderTables orderTables = new OrderTables(savedOrderTables);
        if (orderTables.canGroup(orderTablesIds)) {
            return orderTables;
        }
        throw new IllegalArgumentException();
    }

    @Transactional
    public void ungroup(final Long tableGroupId) {
        OrderTables orderTables = generateOrderTables(tableGroupId);

        orderTables = orderTables.changeTableGroupId(null);
        orderTables.getOrderTables().forEach(orderTableDao::save);
    }

    private OrderTables generateOrderTables(Long tableGroupId) {
        OrderTables orderTables = new OrderTables(orderTableDao.findAllByTableGroupId(tableGroupId));

        List<Long> orderTableIds = orderTables.getOrderTableIds();

        validateExistNotCompletedOrder(orderTableIds);
        return orderTables;
    }

    private void validateExistNotCompletedOrder(List<Long> orderTableIds) {
        if (orderDao.existsByOrderTableIdInAndOrderStatusIn(
                orderTableIds, Arrays.asList(OrderStatus.COOKING.name(), OrderStatus.MEAL.name()))) {
            throw new IllegalArgumentException();
        }
    }
}
