// const {
// 	ClassicEditor,
// 	Autoformat,
// 	Autosave,
// 	BlockQuote,
// 	Bold,
// 	Essentials,
// 	FontBackgroundColor,
// 	FontColor,
// 	FontFamily,
// 	FontSize,
// 	Heading,
// 	Indent,
// 	IndentBlock,
// 	Italic,
// 	Link,
// 	List,
// 	Mention,
// 	Paragraph,
// 	TextTransformation,
// 	TodoList,
// 	Underline,
// 	Emoji
// } = window.CKEDITOR;
//
// // ✅ CKEditor 설정
// const editorConfig = {
// 	toolbar: {
// 		items: [
// 			'undo',
// 			'redo',
// 			'|',
// 			'heading',
// 			'|',
// 			'fontSize',
// 			'fontFamily',
// 			'fontColor',
// 			'fontBackgroundColor',
// 			'|',
// 			'bold',
// 			'italic',
// 			'underline',
// 			'|',
// 			'link',
// 			'blockQuote',
// 			'emoji',
// 			'|',
// 			'bulletedList',
// 			'numberedList',
// 			'todoList',
// 			'outdent',
// 			'indent'
// 		],
// 		shouldNotGroupWhenFull: false
// 	},
// 	plugins: [
// 		Autoformat,
// 		Autosave,
// 		BlockQuote,
// 		Bold,
// 		Essentials,
// 		FontBackgroundColor,
// 		FontColor,
// 		FontFamily,
// 		FontSize,
// 		Heading,
// 		Indent,
// 		IndentBlock,
// 		Italic,
// 		Link,
// 		List,
// 		Mention,
// 		Paragraph,
// 		TextTransformation,
// 		TodoList,
// 		Underline,
// 		Emoji
// 	],
// 	fontFamily: {
// 		supportAllValues: true
// 	},
// 	fontSize: {
// 		options: [10, 12, 14, 'default', 18, 20, 22],
// 		supportAllValues: true
// 	},
// 	heading: {
// 		options: [
// 			{ model: 'paragraph', title: '본문', class: 'ck-heading_paragraph' },
// 			{ model: 'heading1', view: 'h1', title: '제목 1', class: 'ck-heading_heading1' },
// 			{ model: 'heading2', view: 'h2', title: '제목 2', class: 'ck-heading_heading2' },
// 			{ model: 'heading3', view: 'h3', title: '제목 3', class: 'ck-heading_heading3' }
// 		]
// 	},
// 	language: 'ko',
// 	link: {
// 		addTargetToExternalLinks: true,
// 		defaultProtocol: 'https://',
// 		decorators: {
// 			toggleDownloadable: {
// 				mode: 'manual',
// 				label: '파일로 다운로드',
// 				attributes: { download: 'file' }
// 			}
// 		}
// 	},
// 	mention: {
// 		feeds: [
// 			{
// 				marker: '@',
// 				feed: []
// 			}
// 		]
// 	},
// 	placeholder: '내용을 입력하세요...'
// };

// ✅ 에디터 초기화
document.addEventListener('DOMContentLoaded', () => {
	const editorElement = document.querySelector('#editor');
	if (!editorElement) {
		console.error('❌ CKEditor 초기화 실패: #editor 요소를 찾을 수 없습니다.');
		return;
	}

	ClassicEditor
		.create(editorElement, {
			language: 'ko',
			toolbar: [
				'undo', 'redo', '|',
				'heading', '|',
				'bold', 'italic', 'underline', '|',
				'link', 'blockQuote', '|',
				'bulletedList', 'numberedList', '|',
				'outdent', 'indent'
			],
			placeholder: '내용을 입력하세요...'
		})
		.then(editor => {
			console.log('✅ CKEditor 초기화 완료');
			window.editor = editor;
		})
		.catch(error => {
			console.error('❌ CKEditor 초기화 중 오류 발생:', error);
		});
});