package searchengine.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import searchengine.model.Page;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PageRelevance {
    private Page page;
    private Float absRelevance = 0f;
    private Float relRelevance = 0f;

    public PageRelevance(Page page) {
        this.page = page;
    }
}
