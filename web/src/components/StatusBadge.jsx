export default function StatusBadge({ status, label }) {
  const value = status ?? 'NONE'
  return (
    <span className={`badge badge-${value.toLowerCase()}`}>
      {label ? `${label}: ${value}` : value}
    </span>
  )
}
