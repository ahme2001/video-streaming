export default function StatusBadge({ status }) {
  const value = status ?? 'NONE'
  return <span className={`badge badge-${value.toLowerCase()}`}>{value}</span>
}
