import logging
import smtplib
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
        self.port = config['port']
        self.use_tls = config.get('use_tls', False)
        self.use_ssl = config.get('use_ssl', False)
        self.username = config.get('username')
        self.password = config.get('password')

    def send(self, from_addr: str, to_addrs: list, subject: str,
            text_content: str, html_content: Optional[str] = None) -> Tuple[bool, Optional[str], Optional[str]]:
        try:
            msg = MIMEMultipart('alternative')
            msg['From'] = from_addr
            msg['To'] = ', '.join(to_addrs)
            msg['Subject'] = subject
            msg['Date'] = formatdate(localtime=True)
            msg['Message-ID'] = make_msgid(domain=self.host.split('.')[-1] if '.' in self.host else 'localhost')

            msg.attach(MIMEText(text_content, 'plain', 'utf-8'))
            if html_content:
                msg.attach(MIMEText(html_content, 'html', 'utf-8'))

            if self.use_ssl:
                server = smtplib.SMTP_SSL(self.host, self.port)
            else:
                server = smtplib.SMTP(self.host, self.port)

            if self.use_tls:
                server.starttls()

            if self.username and self.password:
                server.login(self.username, self.password)

            server.send_message(msg)
            server.quit()

            message_id = msg['Message-ID']
            logger.info(f"Email sent successfully, Message-ID: {message_id}")
            return True, message_id, None

        except Exception as e:
            error_msg = str(e)
            logger.error(f"SMTP sending failed: {error_msg}")
            return False, None, error_msg