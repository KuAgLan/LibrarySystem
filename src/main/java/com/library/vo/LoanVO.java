package com.library.vo;

import com.library.entity.Loan;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 借阅记录 + 图书/读者信息 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LoanVO extends Loan {

    private String bookTitle;
    private String bookIsbn;
    private String bookCategory;
    private String readerName;
    private String readerCardNo;
    private String readerType;
    private Integer overdue;
}
