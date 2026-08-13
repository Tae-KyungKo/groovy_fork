// Study.EXP_PER_LEVEL(백엔드)과 동일한 기준으로 진행률을 계산한다.
const EXP_PER_LEVEL = 100;

interface LevelBadgeProps {
  level: number;
  expPoint?: number;
}

export function LevelBadge({ level, expPoint }: LevelBadgeProps) {
  const progress = expPoint !== undefined ? expPoint % EXP_PER_LEVEL : undefined;

  return (
    <span
      className="level-badge"
      title={progress !== undefined ? `${progress}/${EXP_PER_LEVEL} EXP` : undefined}
    >
      Lv.{level}
    </span>
  );
}
