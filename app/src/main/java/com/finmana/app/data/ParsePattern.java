package com.finmana.app.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "parse_patterns",
    indices = @Index(value = {"sourcePackage", "regex"}, unique = true)
)
public class ParsePattern {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String sourcePackage;
    public String regex;
    public int amountGroup;
    public Integer directionGroup;
    public String incomeWords;
    public String expenseWords;
    public boolean learnedByAi;
    public long createdAt;

    public ParsePattern() {
        this.amountGroup = 1;
        this.directionGroup = null;
        this.incomeWords = "cong,nhan,vao,+";
        this.expenseWords = "tru,chi,ra,-";
        this.learnedByAi = false;
        this.createdAt = System.currentTimeMillis();
    }

    public ParsePattern(String sourcePackage, String regex, int amountGroup,
                        Integer directionGroup, String incomeWords, String expenseWords,
                        boolean learnedByAi) {
        this.sourcePackage = sourcePackage;
        this.regex = regex;
        this.amountGroup = amountGroup;
        this.directionGroup = directionGroup;
        this.incomeWords = incomeWords != null ? incomeWords : "cong,nhan,vao,+";
        this.expenseWords = expenseWords != null ? expenseWords : "tru,chi,ra,-";
        this.learnedByAi = learnedByAi;
        this.createdAt = System.currentTimeMillis();
    }
}
