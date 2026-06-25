package pk.ak.pasir_krawiec_antoni.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pk.ak.pasir_krawiec_antoni.dto.GroupTransactionDTO;
import pk.ak.pasir_krawiec_antoni.model.Debt;
import pk.ak.pasir_krawiec_antoni.model.Group;
import pk.ak.pasir_krawiec_antoni.model.Membership;
import pk.ak.pasir_krawiec_antoni.model.User;
import pk.ak.pasir_krawiec_antoni.repository.DebtRepository;
import pk.ak.pasir_krawiec_antoni.repository.GroupRepository;
import pk.ak.pasir_krawiec_antoni.repository.MembershipRepository;
import pk.ak.pasir_krawiec_antoni.websocket.GroupNotificationHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GroupTransactionService {

    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final DebtRepository debtRepository;
    private final MembershipService membershipService;
    private final GroupNotificationHandler groupNotificationHandler;
    private final ObjectMapper objectMapper;


    public GroupTransactionService(
            GroupRepository groupRepository,
            MembershipRepository membershipRepository,
            DebtRepository debtRepository,
            MembershipService membershipService,
            GroupNotificationHandler groupNotificationHandler) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.debtRepository = debtRepository;
        this.membershipService = membershipService;
        this.groupNotificationHandler = groupNotificationHandler;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public void addGroupTransaction(GroupTransactionDTO transactionDTO, User currentUser) {
        Group group = groupRepository.findById(transactionDTO.getGroupId())
                .orElseThrow(() -> new EntityNotFoundException("Nie znaleziono Grupy"));

        membershipService.assertCurrentUserIsGroupMember(group.getId());

        List<Membership> members = membershipRepository.findByGroupId(group.getId());

        List<Membership> selectedMembers = selectParticipants(transactionDTO, members, currentUser);

        if (selectedMembers.isEmpty()) {
            throw new IllegalStateException("Grupa nie ma członków, nie można dodać transakcji.");
        }

        double amountPerUser = transactionDTO.getAmount() / selectedMembers.size();
        boolean expense = "EXPENSE".equals(transactionDTO.getType());

        for (Membership member : selectedMembers) {
            User otherUser = member.getUser();

            if (!otherUser.getId().equals(currentUser.getId())) {
                Debt debt = new Debt();
                debt.setDebtor(expense ? otherUser : currentUser);
                debt.setCreditor(expense ? currentUser : otherUser);
                debt.setGroup(group);
                debt.setAmount(amountPerUser);
                debt.setTitle(transactionDTO.getTitle());

                debtRepository.save(debt);

                sendGroupExpenseNotification(group, transactionDTO, amountPerUser, currentUser, otherUser);
            }
        }
    }

    private void sendGroupExpenseNotification(Group group, GroupTransactionDTO dto, double userShare, User creator, User recipient) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "GROUP_EXPENSE_ADDED");
            payload.put("groupId", group.getId());
            payload.put("groupName", group.getName());
            payload.put("title", dto.getTitle());
            payload.put("amount", dto.getAmount());
            payload.put("userShare", userShare);
            payload.put("createdByEmail", creator.getEmail());

            String textMessage = String.format("%s dodał wydatek \"%s\" w grupie %s. Twoja część: %.2f zł.",
                    creator.getEmail(), dto.getTitle(), group.getName(), userShare);
            payload.put("message", textMessage);

            String jsonString = objectMapper.writeValueAsString(payload);
            groupNotificationHandler.sendNotification(recipient.getEmail(), jsonString);
        } catch (Exception e) {
            System.err.println("Nie udało się przygotować powiadomienia JSON: " + e.getMessage());
        }
    }

    private List<Membership> selectParticipants(
            GroupTransactionDTO transactionDTO,
            List<Membership> members,
            User currentUser) {

        List<Long> selectedUserIds = transactionDTO.getSelectedUserIds();

        if (selectedUserIds == null || selectedUserIds.isEmpty()) {
            return members;
        }

        Set<Long> uniqueSelectedUserIds = new HashSet<>(selectedUserIds);
        List<Membership> selectedMembers = members.stream()
                .filter(membership -> uniqueSelectedUserIds.contains(membership.getUser().getId()))
                .toList();

        if (selectedMembers.size() != uniqueSelectedUserIds.size()) {
            throw new IllegalStateException("Wszyscy wybrani użytkownicy muszą być członkami grupy.");
        }

        boolean currentUserSelected = selectedMembers.stream()
                .anyMatch(membership -> membership.getUser().getId().equals(currentUser.getId()));

        if (!currentUserSelected) {
            throw new IllegalStateException("Aktualny użytkownik musi być uczestnikiem transakcji grupowej.");
        }

        if (selectedMembers.size() < 2) {
            throw new IllegalStateException("Transakcja grupowa wymaga co najmniej dwóch uczestników.");
        }

        return selectedMembers;
    }
}