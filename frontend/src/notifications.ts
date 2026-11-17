export function copied(message='Copied successfully') { window.dispatchEvent(new CustomEvent('celpip-copy-success',{detail:message})) }
