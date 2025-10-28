class NotificationManager {
    constructor() {
        this.stompClient = null;
        this.connected = false;
        this.userId = null;
        this.notifications = [];

        this.init();
    }

    // 초기화
    init() {
        this.getUserId();
        this.requestNotificationPermission();
        this.connectWebSocket();
        this.setupNotificationUI();
    }

    // 사용자 ID 가져오기 (header.html의 hidden 값에서)
    getUserId() {
        const userIdElement = document.querySelector('[data-user-id]');
        this.userId = userIdElement ? userIdElement.dataset.userId : null;
        console.log('📍 사용자 ID:', this.userId);
    }

    // 브라우저 알림 권한 요청
    async requestNotificationPermission() {
        if (!("Notification" in window)) {
            console.warn('⚠️ 이 브라우저는 알림을 지원하지 않습니다.');
            return false;
        }

        if (Notification.permission === 'granted') {
            console.log('✅ 알림 권한이 이미 허용되어 있습니다.');
            return true;
        }

        if (Notification.permission === 'denied') {
            console.warn('⚠️ 알림 권한이 거부되어 있습니다.');
            return false;
        }

        // 권한 요청
        const permission = await Notification.requestPermission();
        if (permission === 'granted') {
            console.log('✅ 알림 권한이 허용되었습니다.');
            this.showTestNotification();
            return true;
        } else {
            console.warn('⚠️ 알림 권한이 거부되었습니다.');
            return false;
        }
    }

    // 테스트 알림 표시
    showTestNotification() {
        const notification = new Notification('🔔 알림이 활성화되었습니다!', {
            body: '이제 일정 알림을 실시간으로 받을 수 있습니다.',
            icon: '/favicon.ico',
            tag: 'test-notification'
        });

        setTimeout(() => {
            notification.close();
        }, 3000);
    }

    // WebSocket 연결
    connectWebSocket() {
        if (!this.userId) {
            console.warn('⚠️ 사용자 ID가 없어서 WebSocket 연결을 건너뜁니다.');
            return;
        }

        try {
            const socket = new SockJS('/ws-notifications');
            this.stompClient = Stomp.over(socket);

            // 디버그 로그 비활성화 (선택사항)
            this.stompClient.debug = null;

            this.stompClient.connect({}, 
                (frame) => {
                    console.log('✅ WebSocket 연결됨:', frame);
                    this.connected = true;
                    this.subscribeToNotifications();
                },
                (error) => {
                    console.error('❌ WebSocket 연결 실패:', error);
                    this.connected = false;
                    // 재연결 시도
                    setTimeout(() => {
                        console.log('🔄 WebSocket 재연결 시도...');
                        this.connectWebSocket();
                    }, 5000);
                }
            );

        } catch (error) {
            console.error('❌ WebSocket 연결 중 오류:', error);
        }
    }

    // 알림 구독
    subscribeToNotifications() {
        if (!this.stompClient || !this.connected) {
            console.warn('⚠️ WebSocket이 연결되지 않았습니다.');
            return;
        }

        // 개별 사용자 알림 구독
        this.stompClient.subscribe(`/user/queue/notifications`, (message) => {
            const notification = JSON.parse(message.body);
            console.log('🔔 개별 알림 수신:', notification);
            this.handleNotification(notification);
        });

        // 전체 브로드캐스트 구독
        this.stompClient.subscribe('/topic/notifications', (message) => {
            const notification = JSON.parse(message.body);
            console.log('📢 브로드캐스트 알림 수신:', notification);
            this.handleNotification(notification);
        });

        console.log('✅ 알림 구독 완료');
    }

    // 알림 처리
    handleNotification(notification) {
        // 알림 목록에 추가
        this.notifications.unshift(notification);

        // 브라우저 알림 표시
        this.showBrowserNotification(notification);

        // 챗봇 알림 표시
        this.showChatbotNotification(notification);

        // 헤더 알림 아이콘 업데이트
        this.updateNotificationBadge();

        // 사운드 재생 (선택사항)
        this.playNotificationSound();
    }

    // 브라우저 알림 표시
    showBrowserNotification(notification) {
        if (Notification.permission !== 'granted') {
            console.warn('⚠️ 알림 권한이 없습니다.');
            return;
        }

        const browserNotification = new Notification(notification.title, {
            body: notification.message,
            icon: '/favicon.ico',
            tag: `notification-${Date.now()}`,
            requireInteraction: true // 사용자가 클릭할 때까지 유지
        });

        // 클릭 시 해당 페이지로 이동
        browserNotification.onclick = () => {
            window.focus();
            if (notification.type === 'schedule') {
                window.open('/schedule/manager', '_blank');
            }
            browserNotification.close();
        };

        // 5초 후 자동 닫기
        setTimeout(() => {
            browserNotification.close();
        }, 5000);
    }

    // 챗봇 알림 표시
    showChatbotNotification(notification) {
        // 챗봇 모달이 열려있으면 바로 표시
        if (window.chatbotManager && window.chatbotManager.isOpen()) {
            window.chatbotManager.addNotificationMessage(notification);
        }

        // 챗봇 알림 큐에 추가 (모달이 열릴 때 표시용)
        if (!window.chatbotNotificationQueue) {
            window.chatbotNotificationQueue = [];
        }
        window.chatbotNotificationQueue.push(notification);
    }

    // 헤더 알림 아이콘 업데이트
    updateNotificationBadge() {
        const unreadCount = this.notifications.filter(n => !n.read).length;
        const badge = document.querySelector('.notification-badge');

        if (badge) {
            if (unreadCount > 0) {
                badge.textContent = unreadCount > 99 ? '99+' : unreadCount;
                badge.style.display = 'inline-block';
            } else {
                badge.style.display = 'none';
            }
        }
    }

    // 알림 사운드 재생
    playNotificationSound() {
        try {
            const audio = new Audio('/sounds/notification.mp3'); // 사운드 파일 필요
            audio.volume = 0.3;
            audio.play().catch(e => {
                console.log('사운드 재생 실패 (자동재생 정책):', e);
            });
        } catch (error) {
            console.log('사운드 파일 없음:', error);
        }
    }

    // 연결 해제
    disconnect() {
        if (this.stompClient && this.connected) {
            this.stompClient.disconnect();
            console.log('🔌 WebSocket 연결 해제됨');
        }
    }
}

// 전역 알림 매니저 인스턴스
let notificationManager = null;

// 페이지 로드 시 알림 시스템 초기화
document.addEventListener('DOMContentLoaded', function() {
    notificationManager = new NotificationManager();
});

// 페이지 언로드 시 연결 해제
window.addEventListener('beforeunload', function() {
    if (notificationManager) {
        notificationManager.disconnect();
    }
});