package com.hotel.syncmonitor.connector;

import com.hotel.syncmonitor.domain.enums.PmsSyncStatus;
import com.hotel.syncmonitor.domain.enums.RoomType;
import com.hotel.syncmonitor.dto.BookingReservationDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MockBookingReservationConnectorTest {

    private final MockBookingReservationConnector connector = new MockBookingReservationConnector();

    @Test
    void returnsDemoScenarioWithUniqueChannelAndExternalIds() {
        List<BookingReservationDto> feed = connector.fetchNewReservations();

        assertThat(feed).hasSize(8);
        assertThat(feed).extracting(d -> d.channel() + "/" + d.externalReservationId())
                .doesNotHaveDuplicates();
    }

    @Test
    void everyStayHasCheckOutAfterCheckInAndPositiveGuests() {
        assertThat(connector.fetchNewReservations()).allSatisfy(d -> {
            assertThat(d.checkOutDate()).isAfter(d.checkInDate());
            assertThat(d.guestCount()).isPositive();
        });
    }

    @Test
    void importedReservationsCarryNonNegativeSyncDelay() {
        assertThat(connector.fetchNewReservations())
                .filteredOn(d -> d.pmsImportedAt() != null)
                .allSatisfy(d -> assertThat(d.pmsImportedAt()).isAfterOrEqualTo(d.emailReceivedAt()));
    }

    @Test
    void containsExactlyOnePendingImportWithoutPmsTimestamp() {
        assertThat(connector.fetchNewReservations())
                .filteredOn(d -> d.pmsSyncStatus() == PmsSyncStatus.PENDING_IMPORT)
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.externalReservationId()).isEqualTo("BKG-007");
                    assertThat(d.pmsImportedAt()).isNull();
                });
    }

    @Test
    void scenarioHasFourDoubleReservationsForReshuffleDemo() {
        assertThat(connector.fetchNewReservations())
                .filteredOn(d -> d.roomType() == RoomType.DOUBLE)
                .hasSize(4);
    }

    @Test
    void acknowledgeIsANoOpForMock() {
        assertThatCode(() -> connector.acknowledge("BKG-001")).doesNotThrowAnyException();
    }
}
