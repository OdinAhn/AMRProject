import { Button } from "../ui";

interface VersionCreateFormProps {
  onSubmit: () => void;
  isPending: boolean;
}

export function VersionCreateForm({ onSubmit, isPending }: VersionCreateFormProps) {
  return (
    <Button onClick={onSubmit} disabled={isPending}>
      새 버전(DRAFT) 만들기
    </Button>
  );
}
