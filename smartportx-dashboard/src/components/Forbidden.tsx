import { Link } from 'react-router-dom';
import { Icon } from './icons';

export function Forbidden() {
  return (
    <div className="empty-state">
      <Icon name="alert" size={26} />
      <div style={{ marginTop: 6, fontSize: 15, fontWeight: 600 }}>Access denied</div>
      <p>You do not have permission to view this page.</p>
      <Link className="btn btn-sm" to="/dashboard">
        Back to dashboard
      </Link>
    </div>
  );
}

export default Forbidden;