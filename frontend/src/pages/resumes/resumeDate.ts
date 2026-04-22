export function formatUploadDate(d: string | null | undefined): string {
  if (!d) return '-';
  const parsed = new Date(d);
  return Number.isNaN(parsed.getTime()) ? '-' : parsed.toLocaleDateString();
}
