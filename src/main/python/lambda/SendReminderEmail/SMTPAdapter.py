import logging
import smtplib
from contextlib import contextmanager
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.utils import formatdate, make_msgid
from typing import Dict, Any, Optional, Tuple

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class SMTPAdapter:
    def __init__(self, config: Dict[str, Any]):
        self.config = config
        self.host = config['host']
        self.port = config.get('port', 587)
        self.use_tls = config.get('use_tls', False)
        self.use_ssl = config.get('use_ssl', False)
        self.username = config.get('username')
        self.password = config.get('password')

    @contextmanager
    def _get_connection(self):
        conn = None
        try:
            if self.use_ssl:
                conn = smtplib.SMTP_SSL(self.host, self.port, timeout=10)
            else:
                conn = smtplib.SMTP(self.host, self.port, timeout=10)
                if self.use_tls:
                    conn.starttls()
            yield conn
        finally:
            if conn:
                try:
                    conn.quit()
                except Exception:
                    pass

    def send(self, from_addr: str, to_addrs: list, subject: str,
             text_content: str, html_content: Optional[str] = None) -> Tuple[bool, Optional[str], Optional[str]]:
        try:
            msg = MIMEMultipart('alternative')
            msg['From'] = from_addr
            msg['To'] = ', '.join(to_addrs)
            msg['Subject'] = subject
            msg['Date'] = formatdate(localtime=True)
            msg['Message-ID'] = make_msgid(domain=self.host.split('.')[-1] if '.' in self.host else 'localhost')

            if html_content:
                msg.attach(MIMEText(html_content, 'html', 'utf-8'))

            msg.attach(MIMEText(text_content, 'plain', 'utf-8'))
            with self._get_connection() as server:
                if self.username and self.password:
                    server.login(self.username, self.password)
                server.send_message(msg)
                return True, msg['Message-ID'], None
        except smtplib.SMTPAuthenticationError:
            return False, None, "Invalid credentials"
        except smtplib.SMTPException as e:
            return False, None, f"SMTP Error: {e}"
        except Exception as e:
            return False, None, str(e)