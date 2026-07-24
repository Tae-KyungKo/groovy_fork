import type { Tag } from "../types";

interface TagPickerProps {
  tags: Tag[];
  selected: number[];
  onToggle: (tagId: number) => void;
}

export function TagPicker({ tags, selected, onToggle }: TagPickerProps) {
  return (
    <div className="tag-picker">
      {tags.map((tag) => {
        const active = selected.includes(tag.id);
        return (
          <button
            key={tag.id}
            type="button"
            className={`tag-chip${active ? " active" : ""}`}
            onClick={() => onToggle(tag.id)}
          >
            #{tag.name}
          </button>
        );
      })}
    </div>
  );
}
