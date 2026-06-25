package pk.ak.pasir_krawiec_antoni.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BalanceDTO {

    private double totalIncome;
    private double totalExpense;
    private double balance;
}