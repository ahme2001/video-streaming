import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import UploadPage from './pages/UploadPage.jsx'
import WatchPage from './pages/WatchPage.jsx'

export default function App() {
  return (
    <div className="app">
      <header>
        <h1>Video streaming techniques</h1>
        <nav>
          <NavLink to="/upload">Upload</NavLink>
          <NavLink to="/watch">Watch</NavLink>
        </nav>
      </header>

      <main>
        <Routes>
          <Route path="/upload" element={<UploadPage />} />
          <Route path="/watch" element={<WatchPage />} />
          <Route path="*" element={<Navigate to="/watch" replace />} />
        </Routes>
      </main>
    </div>
  )
}
